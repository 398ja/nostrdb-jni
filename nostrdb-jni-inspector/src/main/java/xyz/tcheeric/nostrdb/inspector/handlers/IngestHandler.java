package xyz.tcheeric.nostrdb.inspector.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import xyz.tcheeric.nostrdb.Ndb;

import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for the ingest page and API.
 */
public class IngestHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;
    private final boolean readOnly;

    public IngestHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock, boolean readOnly) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
        this.readOnly = readOnly;
    }

    /**
     * GET /ingest — ingest page.
     */
    public Handler page() {
        return ctx -> {
            ctx.render("pages/ingest.jte", Map.of("readOnly", readOnly));
        };
    }

    /**
     * POST /api/ingest — ingest events.
     */
    public Handler ingest() {
        return ctx -> {
            if (readOnly) {
                ctx.status(HttpStatus.FORBIDDEN)
                   .json(Map.of("error", "Ingest disabled in read-only mode"));
                return;
            }

            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();
                String contentType = ctx.contentType() != null ? ctx.contentType() : "";
                int processed = 0;
                int total = 0;
                int errors = 0;

                if (contentType.contains("application/json")) {
                    // JSON object with "events" array
                    JsonNode root = MAPPER.readTree(ctx.body());
                    JsonNode events = root.get("events");
                    if (events != null && events.isArray()) {
                        total = events.size();
                        for (JsonNode event : events) {
                            try {
                                String json = "[\"EVENT\",\"inspector\"," + event.toString() + "]";
                                ndb.processEvent(json);
                                processed++;
                            } catch (Exception e) {
                                errors++;
                            }
                        }
                    }
                } else {
                    // Line-delimited JSON/text
                    String body = ctx.body();
                    String[] lines = body.split("\n");
                    total = lines.length;
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            total--;
                            continue;
                        }
                        try {
                            ndb.processEvent(line);
                            processed++;
                        } catch (Exception e) {
                            errors++;
                        }
                    }
                }

                Map<String, Object> result = Map.of(
                        "processed", processed,
                        "total", total,
                        "errors", errors
                );

                // Return fragment if HTMX request, otherwise JSON
                if (ctx.header("HX-Request") != null) {
                    ctx.render("fragments/ingest-result.jte", Map.of("result", result));
                } else {
                    ctx.json(result);
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }
}
