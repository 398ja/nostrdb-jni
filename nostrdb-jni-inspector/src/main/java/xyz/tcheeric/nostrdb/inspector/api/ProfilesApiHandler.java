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
 * Handler for profile API endpoints.
 */
public class ProfilesApiHandler {

    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;

    public ProfilesApiHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
    }

    /**
     * GET /api/profiles — search profiles by name.
     */
    public Handler search() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                Ndb ndb = ndbRef.get();
                String query = ctx.queryParam("q");
                int limit = Math.min(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20), 100);

                try (Transaction txn = ndb.beginTransaction()) {
                    List<byte[]> pubkeys = ndb.searchProfiles(txn, query, limit);

                    List<Map<String, Object>> profiles = new ArrayList<>();
                    for (byte[] pubkey : pubkeys) {
                        ndb.getProfileByPubkey(txn, pubkey).ifPresent(profile -> {
                            profiles.add(profileToMap(encodeHex(pubkey), profile));
                        });
                    }

                    ctx.json(Map.of(
                            "total", profiles.size(),
                            "profiles", profiles
                    ));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    /**
     * GET /api/profiles/:pubkey — get a single profile.
     */
    public Handler getByPubkey() {
        return ctx -> {
            rwLock.readLock().lock();
            try {
                String pubkey = ctx.pathParam("pubkey");
                if (!Formatting.isValidHex64(pubkey)) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                       .json(Map.of("error", "Invalid pubkey format"));
                    return;
                }

                Ndb ndb = ndbRef.get();
                try (Transaction txn = ndb.beginTransaction()) {
                    Optional<Profile> profile = ndb.getProfileByPubkey(txn, pubkey);
                    if (profile.isEmpty()) {
                        ctx.status(HttpStatus.NOT_FOUND)
                           .json(Map.of("error", "Profile not found"));
                        return;
                    }
                    ctx.json(profileToMap(pubkey, profile.get()));
                }
            } finally {
                rwLock.readLock().unlock();
            }
        };
    }

    private Map<String, Object> profileToMap(String pubkey, Profile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pubkey", pubkey);
        map.put("name", profile.name());
        map.put("display_name", profile.displayName());
        map.put("about", profile.about());
        map.put("picture", profile.picture());
        map.put("banner", profile.banner());
        map.put("nip05", profile.nip05());
        map.put("lud16", profile.lud16());
        map.put("website", profile.website());
        return map;
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
