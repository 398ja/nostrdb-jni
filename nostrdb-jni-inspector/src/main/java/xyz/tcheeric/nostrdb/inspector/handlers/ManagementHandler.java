package xyz.tcheeric.nostrdb.inspector.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import xyz.tcheeric.nostrdb.Ndb;
import xyz.tcheeric.nostrdb.NdbStat;
import xyz.tcheeric.nostrdb.inspector.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for database management page and flush API.
 */
public class ManagementHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;
    private final boolean readOnly;

    public ManagementHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock, boolean readOnly) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
        this.readOnly = readOnly;
    }

    /**
     * GET /management — management page.
     */
    public Handler page() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();
                ctx.render("pages/management.jte", Map.of(
                        "readOnly", readOnly,
                        "dbPath", ndb.getDbPath(),
                        "fileSize", Formatting.formatSize(ndb.getDbFileSize()),
                        "totalEntries", ndb.getStats().totalEventCount()
                ));
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * POST /api/flush — flush the database.
     */
    public Handler flush() {
        return ctx -> {
            if (readOnly) {
                ctx.status(HttpStatus.FORBIDDEN)
                   .json(Map.of("error", "Flush disabled in read-only mode"));
                return;
            }

            // Parse and validate confirmation
            JsonNode body = MAPPER.readTree(ctx.body());
            if (body == null || !body.has("confirm") || !body.get("confirm").asBoolean()) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .json(Map.of("error", "Confirmation required: {\"confirm\": true}"));
                return;
            }

            rwLock.writeLock().lock();
            try {
                Ndb ndb = ndbRef.get();

                // Check for active subscriptions
                int subCount = ndb.getSubscriptionCount();
                if (subCount > 0) {
                    ctx.status(HttpStatus.CONFLICT)
                       .json(Map.of("error", "Cannot flush: " + subCount + " active subscriptions"));
                    return;
                }

                // Record current stats
                NdbStat stats = ndb.getStats();
                long previousFileSize = ndb.getDbFileSize();
                long previousEntries = stats.totalEventCount();
                String dbPath = ndb.getDbPath();

                // Close the database
                ndb.close();

                // Delete data files
                try {
                    Files.deleteIfExists(Path.of(dbPath, "data.mdb"));
                    Files.deleteIfExists(Path.of(dbPath, "lock.mdb"));
                } catch (IOException e) {
                    // Try to reopen even if delete partially failed
                    Ndb reopened = Ndb.open(dbPath);
                    ndbRef.set(reopened);
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                       .json(Map.of("error", "Failed to delete database files: " + e.getMessage()));
                    return;
                }

                // Reopen fresh database
                Ndb reopened = Ndb.open(dbPath);
                ndbRef.set(reopened);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "ok");
                result.put("message", "Database flushed and reopened");
                result.put("previousFileSize", previousFileSize);
                result.put("previousEntries", previousEntries);

                if (ctx.header("HX-Request") != null) {
                    ctx.render("fragments/flush-result.jte", Map.of("result", result));
                } else {
                    ctx.json(result);
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        };
    }
}
