package xyz.tcheeric.nostrdb.inspector.handlers;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import xyz.tcheeric.nostrdb.*;
import xyz.tcheeric.nostrdb.inspector.model.NoteViewModel;
import xyz.tcheeric.nostrdb.inspector.model.NotesFilter;
import xyz.tcheeric.nostrdb.inspector.model.NotesPageModel;
import xyz.tcheeric.nostrdb.inspector.util.Formatting;
import xyz.tcheeric.nostrdb.inspector.util.KindLabels;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for note browser pages.
 */
public class NotesHandler {

    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;

    public NotesHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
    }

    /**
     * GET /notes — note browser page.
     */
    public Handler list() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();
                NotesPageModel model = buildNotesPage(ctx, ndb);
                ctx.render("pages/notes.jte", Map.of("model", model));
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * GET /notes/:id — note detail page.
     */
    public Handler detail() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                String id = ctx.pathParam("id");
                if (!Formatting.isValidHex64(id)) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Invalid event ID");
                    return;
                }

                Ndb ndb = ndbRef.get();
                try (Transaction txn = ndb.beginTransaction()) {
                    Optional<Note> note = ndb.getNoteById(txn, id);
                    if (note.isEmpty()) {
                        ctx.status(HttpStatus.NOT_FOUND).result("Note not found");
                        return;
                    }

                    NoteViewModel viewModel = toViewModel(note.get(), ndb, txn);
                    ctx.render("pages/note-detail.jte", Map.of("note", viewModel, "rawJson", note.get().toJson()));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    private NotesPageModel buildNotesPage(Context ctx, Ndb ndb) {
        int limit = Math.min(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50), 1000);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

        NotesFilter activeFilter = parseFilter(ctx);

        Filter.Builder builder = Filter.builder();

        if (!activeFilter.kinds().isEmpty()) {
            builder.kinds(activeFilter.kinds().stream().mapToInt(Integer::intValue).toArray());
        }
        if (!activeFilter.authors().isEmpty()) {
            builder.authors(activeFilter.authors().toArray(new String[0]));
        }
        if (activeFilter.since() != null) {
            builder.since(activeFilter.since());
        }
        if (activeFilter.until() != null) {
            builder.until(activeFilter.until());
        }
        for (String tag : activeFilter.tags()) {
            int colonIdx = tag.indexOf(':');
            if (colonIdx > 0 && colonIdx < tag.length() - 1) {
                builder.tag(tag.substring(0, colonIdx), tag.substring(colonIdx + 1));
            }
        }
        if (activeFilter.search() != null && !activeFilter.search().isBlank()) {
            builder.search(activeFilter.search());
        }

        builder.limit(limit + offset);

        try (Filter filter = builder.build();
             Transaction txn = ndb.beginTransaction()) {

            List<Note> allNotes = ndb.queryNotes(txn, filter, limit + offset);
            int total = allNotes.size();

            List<Note> pageNotes = offset < allNotes.size()
                    ? allNotes.subList(offset, Math.min(offset + limit, allNotes.size()))
                    : List.of();

            List<NoteViewModel> viewModels = pageNotes.stream()
                    .map(note -> toViewModel(note, ndb, txn))
                    .toList();

            return new NotesPageModel(
                    viewModels, activeFilter, total, limit, offset,
                    offset + limit < total,
                    offset > 0
            );
        }
    }

    private NotesFilter parseFilter(Context ctx) {
        List<Integer> kinds = ctx.queryParams("kind").stream()
                .map(Integer::parseInt).toList();
        List<String> authors = ctx.queryParams("author");
        String sinceStr = ctx.queryParam("since");
        String untilStr = ctx.queryParam("until");
        List<String> tags = ctx.queryParams("tag");
        String search = ctx.queryParam("search");

        return new NotesFilter(
                kinds, authors,
                sinceStr != null ? Long.parseLong(sinceStr) : null,
                untilStr != null ? Long.parseLong(untilStr) : null,
                tags, search
        );
    }

    static NoteViewModel toViewModel(Note note, Ndb ndb, Transaction txn) {
        String authorName = null;
        try {
            Optional<Profile> profile = ndb.getProfileByPubkey(txn, note.pubkey());
            if (profile.isPresent()) {
                authorName = profile.get().bestDisplayName();
            }
        } catch (Exception ignored) {
            // Profile lookup is best-effort
        }

        return new NoteViewModel(
                note.id(),
                Formatting.truncateHex(note.id()),
                note.pubkey(),
                Formatting.truncateHex(note.pubkey()),
                authorName,
                note.kind(),
                KindLabels.getLabel(note.kind()),
                note.content(),
                Formatting.truncateContent(note.content(), 200),
                Formatting.formatTimestamp(note.createdAt()),
                Formatting.formatRelativeTime(note.createdAt()),
                note.tags(),
                note.sig()
        );
    }
}
