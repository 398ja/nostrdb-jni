package xyz.tcheeric.nostrdb.inspector.handlers;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import xyz.tcheeric.nostrdb.*;
import xyz.tcheeric.nostrdb.inspector.model.ProfileViewModel;
import xyz.tcheeric.nostrdb.inspector.model.NoteViewModel;
import xyz.tcheeric.nostrdb.inspector.util.Formatting;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for profile browser pages.
 */
public class ProfilesHandler {

    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;

    public ProfilesHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
    }

    /**
     * GET /profiles — profile browser page.
     */
    public Handler list() {
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
                                profiles.add(toViewModel(pubkeyHex, profile, ndb, txn));
                            });
                        }
                    }

                    ctx.render("pages/profiles.jte", Map.of(
                            "profiles", profiles,
                            "query", query != null ? query : ""
                    ));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * GET /profiles/:pubkey — profile detail page.
     */
    public Handler detail() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                String pubkey = ctx.pathParam("pubkey");
                if (!Formatting.isValidHex64(pubkey)) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Invalid pubkey");
                    return;
                }

                Ndb ndb = ndbRef.get();
                try (Transaction txn = ndb.beginTransaction()) {
                    Optional<Profile> profile = ndb.getProfileByPubkey(txn, pubkey);
                    if (profile.isEmpty()) {
                        ctx.status(HttpStatus.NOT_FOUND).result("Profile not found");
                        return;
                    }

                    ProfileViewModel viewModel = toViewModel(pubkey, profile.get(), ndb, txn);

                    // Recent notes by this author
                    List<NoteViewModel> recentNotes = new ArrayList<>();
                    try (Filter filter = Filter.builder()
                            .authors(pubkey)
                            .limit(20)
                            .build()) {
                        List<Note> notes = ndb.queryNotes(txn, filter, 20);
                        for (Note note : notes) {
                            recentNotes.add(NotesHandler.toViewModel(note, ndb, txn));
                        }
                    }

                    ctx.render("pages/profile-detail.jte", Map.of(
                            "profile", viewModel,
                            "recentNotes", recentNotes
                    ));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    static ProfileViewModel toViewModel(String pubkey, Profile profile, Ndb ndb, Transaction txn) {
        // Count notes by this author
        long noteCount = 0;
        try (Filter filter = Filter.builder().authors(pubkey).limit(1000).build()) {
            noteCount = ndb.query(txn, filter, 1000).size();
        } catch (Exception ignored) {
            // Note count is best-effort
        }

        return new ProfileViewModel(
                pubkey,
                Formatting.truncateHex(pubkey),
                profile.name(),
                profile.displayName(),
                profile.bestDisplayName(),
                profile.about(),
                Formatting.truncateContent(profile.about(), 200),
                profile.picture(),
                profile.banner(),
                profile.nip05(),
                profile.lud16(),
                profile.website(),
                noteCount
        );
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
