package xyz.tcheeric.nostrdb.inspector.handlers;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import xyz.tcheeric.nostrdb.*;
import xyz.tcheeric.nostrdb.inspector.model.*;
import xyz.tcheeric.nostrdb.inspector.util.Formatting;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for HTMX fragment endpoints.
 * <p>
 * These endpoints return partial HTML fragments that HTMX swaps into the DOM.
 * If the request is not an HTMX request (no HX-Request header), they redirect
 * to the full page equivalent.
 */
public class FragmentHandler {

    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;

    public FragmentHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
    }

    /**
     * GET /fragments/stats-panel — auto-refreshing stats cards.
     */
    public Handler statsPanel() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();
                DashboardModel model = DashboardHandler.buildDashboardModel(ndb);
                ctx.render("fragments/stats-panel.jte", Map.of("model", model));
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * GET /fragments/kind-table — kind breakdown rows.
     */
    public Handler kindTable() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();
                DashboardModel model = DashboardHandler.buildDashboardModel(ndb);
                ctx.render("fragments/kind-table.jte", Map.of("kindStats", model.kindStats()));
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * GET /fragments/notes-table — note result rows for search/filter/pagination.
     */
    public Handler notesTable() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();
                int limit = Math.min(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50), 1000);
                int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

                Filter.Builder builder = Filter.builder();
                List<String> kinds = ctx.queryParams("kind");
                if (!kinds.isEmpty()) {
                    builder.kinds(kinds.stream().mapToInt(Integer::parseInt).toArray());
                }
                List<String> authors = ctx.queryParams("author");
                if (!authors.isEmpty()) {
                    builder.authors(authors.toArray(new String[0]));
                }
                String search = ctx.queryParam("search");
                if (search != null && !search.isBlank()) {
                    builder.search(search);
                }
                String since = ctx.queryParam("since");
                if (since != null) builder.since(Long.parseLong(since));
                String until = ctx.queryParam("until");
                if (until != null) builder.until(Long.parseLong(until));

                builder.limit(limit + offset);

                try (Filter filter = builder.build();
                     Transaction txn = ndb.beginTransaction()) {

                    List<Note> allNotes = ndb.queryNotes(txn, filter, limit + offset);
                    int total = allNotes.size();

                    List<Note> pageNotes = offset < allNotes.size()
                            ? allNotes.subList(offset, Math.min(offset + limit, allNotes.size()))
                            : List.of();

                    List<NoteViewModel> viewModels = pageNotes.stream()
                            .map(note -> NotesHandler.toViewModel(note, ndb, txn))
                            .toList();

                    ctx.render("fragments/notes-table.jte", Map.of(
                            "notes", viewModels,
                            "total", total,
                            "limit", limit,
                            "offset", offset,
                            "hasNext", offset + limit < total,
                            "hasPrev", offset > 0
                    ));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * GET /fragments/note-row-detail — inline expanded note detail.
     */
    public Handler noteRowDetail() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                String id = ctx.queryParam("id");
                if (id == null || !Formatting.isValidHex64(id)) {
                    ctx.result("Invalid note ID");
                    return;
                }

                Ndb ndb = ndbRef.get();
                try (Transaction txn = ndb.beginTransaction()) {
                    Optional<Note> note = ndb.getNoteById(txn, id);
                    if (note.isEmpty()) {
                        ctx.result("Note not found");
                        return;
                    }
                    NoteViewModel viewModel = NotesHandler.toViewModel(note.get(), ndb, txn);
                    ctx.render("fragments/note-row-detail.jte", Map.of(
                            "note", viewModel,
                            "rawJson", note.get().toJson()
                    ));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * GET /fragments/profiles-grid — profile cards grid.
     */
    public Handler profilesGrid() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();
                String query = ctx.queryParam("q");
                int limit = Math.min(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20), 100);

                try (Transaction txn = ndb.beginTransaction()) {
                    List<ProfileViewModel> profiles = new ArrayList<>();

                    if (query != null && !query.isBlank()) {
                        List<byte[]> pubkeys = ndb.searchProfiles(txn, query, limit);
                        for (byte[] pubkey : pubkeys) {
                            ndb.getProfileByPubkey(txn, pubkey).ifPresent(profile -> {
                                String pubkeyHex = encodeHex(pubkey);
                                profiles.add(ProfilesHandler.toViewModel(pubkeyHex, profile, ndb, txn));
                            });
                        }
                    }

                    ctx.render("fragments/profiles-grid.jte", Map.of("profiles", profiles));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    private static String encodeHex(byte[] bytes) {
        char[] hexChars = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            result[i * 2] = hexChars[v >>> 4];
            result[i * 2 + 1] = hexChars[v & 0x0F];
        }
        return new String(result);
    }
}
