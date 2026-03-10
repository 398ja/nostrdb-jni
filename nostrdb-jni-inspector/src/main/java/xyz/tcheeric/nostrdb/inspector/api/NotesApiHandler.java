package xyz.tcheeric.nostrdb.inspector.api;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import xyz.tcheeric.nostrdb.*;
import xyz.tcheeric.nostrdb.inspector.util.Formatting;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for note API endpoints.
 */
public class NotesApiHandler {

    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;

    public NotesApiHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
    }

    /**
     * GET /api/notes — query notes with filtering and pagination.
     */
    public Handler list() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();

                int limit = Math.min(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50), 1000);
                int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

                try (Filter filter = buildFilter(ctx, limit + offset);
                     Transaction txn = ndb.beginTransaction()) {

                    List<Note> allNotes = ndb.queryNotes(txn, filter, limit + offset);
                    int total = allNotes.size();

                    // Skip offset results (over-fetch pagination)
                    List<Note> notes = offset < allNotes.size()
                            ? allNotes.subList(offset, Math.min(offset + limit, allNotes.size()))
                            : List.of();

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("total", total);
                    response.put("limit", limit);
                    response.put("offset", offset);
                    response.put("notes", notes.stream().map(this::noteToMap).toList());

                    ctx.json(response);
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * GET /api/notes/:id — get a single note by ID.
     */
    public Handler getById() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                String id = ctx.pathParam("id");
                if (!Formatting.isValidHex64(id)) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                       .json(Map.of("error", "Invalid event ID format"));
                    return;
                }

                Ndb ndb = ndbRef.get();
                try (Transaction txn = ndb.beginTransaction()) {
                    Optional<Note> note = ndb.getNoteById(txn, id);
                    if (note.isEmpty()) {
                        ctx.status(HttpStatus.NOT_FOUND)
                           .json(Map.of("error", "Note not found"));
                        return;
                    }
                    ctx.json(noteToMap(note.get()));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    private Filter buildFilter(Context ctx, int limit) {
        Filter.Builder builder = Filter.builder();

        // Kinds
        List<String> kinds = ctx.queryParams("kind");
        if (!kinds.isEmpty()) {
            int[] kindArray = kinds.stream().mapToInt(Integer::parseInt).toArray();
            builder.kinds(kindArray);
        }

        // Authors
        List<String> authors = ctx.queryParams("author");
        if (!authors.isEmpty()) {
            builder.authors(authors.toArray(new String[0]));
        }

        // Since/Until
        String since = ctx.queryParam("since");
        if (since != null) {
            builder.since(Long.parseLong(since));
        }
        String until = ctx.queryParam("until");
        if (until != null) {
            builder.until(Long.parseLong(until));
        }

        // Tags
        List<String> tags = ctx.queryParams("tag");
        for (String tag : tags) {
            int colonIdx = tag.indexOf(':');
            if (colonIdx > 0 && colonIdx < tag.length() - 1) {
                String name = tag.substring(0, colonIdx);
                String value = tag.substring(colonIdx + 1);
                builder.tag(name, value);
            }
        }

        // Search
        String search = ctx.queryParam("search");
        if (search != null && !search.isBlank()) {
            builder.search(search);
        }

        builder.limit(limit);
        return builder.build();
    }

    private Map<String, Object> noteToMap(Note note) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", note.id());
        map.put("pubkey", note.pubkey());
        map.put("created_at", note.createdAt());
        map.put("kind", note.kind());
        map.put("content", note.content());
        map.put("tags", note.tags());
        map.put("sig", note.sig());
        return map;
    }
}
