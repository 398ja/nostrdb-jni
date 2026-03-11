package xyz.tcheeric.nostrdb.inspector.api;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import xyz.tcheeric.nostrdb.*;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for GET /api/search — full-text search.
 */
public class SearchApiHandler implements Handler {

    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;

    public SearchApiHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
    }

    @Override
    public void handle(Context ctx) {
        rwLock.readLock().lock();
        try {
            Ndb ndb = ndbRef.get();
            String query = ctx.queryParam("q");
            int limit = Math.min(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50), 1000);

            Filter.Builder builder = Filter.builder();
            if (query != null && !query.isBlank()) {
                builder.search(query);
            }

            // Optional kind filter
            List<String> kinds = ctx.queryParams("kind");
            if (!kinds.isEmpty()) {
                int[] kindArray = kinds.stream().mapToInt(Integer::parseInt).toArray();
                builder.kinds(kindArray);
            }

            builder.limit(limit);

            try (Filter filter = builder.build();
                 Transaction txn = ndb.beginTransaction()) {

                List<Note> notes = ndb.queryNotes(txn, filter, limit);

                List<Map<String, Object>> noteList = notes.stream().map(note -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", note.id());
                    map.put("pubkey", note.pubkey());
                    map.put("created_at", note.createdAt());
                    map.put("kind", note.kind());
                    map.put("content", note.content());
                    map.put("tags", note.tags());
                    map.put("sig", note.sig());
                    return map;
                }).toList();

                ctx.json(Map.of(
                        "total", noteList.size(),
                        "limit", limit,
                        "offset", 0,
                        "notes", noteList
                ));
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
