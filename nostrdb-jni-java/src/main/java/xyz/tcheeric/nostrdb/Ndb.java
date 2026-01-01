package xyz.tcheeric.nostrdb;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance embedded Nostr event database.
 *
 * <p>This is the main entry point for interacting with nostrdb. It provides methods for:
 * <ul>
 *   <li>Processing (ingesting) Nostr events</li>
 *   <li>Querying events by various criteria</li>
 *   <li>Looking up profiles</li>
 *   <li>Real-time subscriptions</li>
 * </ul>
 *
 * <p>The database is thread-safe for concurrent access. However, each thread should
 * use its own {@link Transaction} (LMDB constraint).
 *
 * <p>Example usage:
 * <pre>{@code
 * try (Ndb ndb = Ndb.open(Path.of("~/.nostrdb"))) {
 *     // Ingest an event
 *     ndb.processEvent(jsonEvent);
 *
 *     // Query events
 *     try (Transaction txn = ndb.beginTransaction();
 *          Filter filter = Filter.builder()
 *              .kinds(1)
 *              .limit(100)
 *              .build()) {
 *
 *         List<Note> notes = ndb.queryNotes(txn, filter, 100);
 *         notes.forEach(note -> System.out.println(note.content()));
 *     }
 * }
 * }</pre>
 */
public final class Ndb implements Closeable {

    private final long ptr;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Ndb(long ptr) {
        this.ptr = ptr;
    }

    /**
     * Open a database at the specified path with default configuration.
     *
     * @param dbPath Path to the database directory (will be created if it doesn't exist)
     * @return The Ndb instance
     * @throws NostrdbException if the database cannot be opened
     */
    public static Ndb open(Path dbPath) {
        return open(dbPath.toString());
    }

    /**
     * Open a database at the specified path with default configuration.
     *
     * @param dbPath Path to the database directory (will be created if it doesn't exist)
     * @return The Ndb instance
     * @throws NostrdbException if the database cannot be opened
     */
    public static Ndb open(String dbPath) {
        long ptr = NostrdbNative.ndbOpen(dbPath, 0);
        if (ptr == 0) {
            throw new NostrdbException("Failed to open database at " + dbPath);
        }
        return new Ndb(ptr);
    }

    /**
     * Process a single Nostr event JSON.
     *
     * <p>The JSON can be in either relay format {@code ["EVENT", "subid", {...}]} or
     * client format {@code ["EVENT", {...}]} or just the event object {@code {...}}.
     *
     * @param json The JSON event string
     * @throws NostrdbException if the event cannot be processed
     */
    public void processEvent(String json) {
        checkOpen();
        int result = NostrdbNative.processEvent(ptr, json);
        if (result == 0) {
            throw new NostrdbException("Failed to process event");
        }
    }

    /**
     * Process multiple events from newline-delimited JSON.
     *
     * @param ldjson Newline-delimited JSON events
     * @return The number of events successfully processed
     */
    public int processEvents(String ldjson) {
        checkOpen();
        int result = NostrdbNative.processEvents(ptr, ldjson);
        if (result < 0) {
            throw new NostrdbException("Failed to process events");
        }
        return result;
    }

    /**
     * Begin a read transaction.
     *
     * <p><b>IMPORTANT:</b> Only one transaction per thread is allowed (LMDB constraint).
     * Always use try-with-resources to ensure proper cleanup.
     *
     * @return A new Transaction
     * @throws NostrdbException if the transaction cannot be started
     */
    public Transaction beginTransaction() {
        checkOpen();
        long txnPtr = NostrdbNative.beginTransaction(ptr);
        if (txnPtr == 0) {
            throw new NostrdbException("Failed to begin transaction");
        }
        return new Transaction(this, txnPtr);
    }

    /**
     * Get a note by its 32-byte event ID.
     *
     * @param txn The transaction
     * @param eventId 32-byte event ID
     * @return The note, or empty if not found
     */
    public Optional<Note> getNoteById(Transaction txn, byte[] eventId) {
        checkOpen();
        if (eventId == null || eventId.length != 32) {
            throw new IllegalArgumentException("Event ID must be 32 bytes");
        }
        byte[] data = NostrdbNative.getNoteById(ptr, txn.ptr(), eventId);
        return Optional.ofNullable(data).map(Note::fromBytes);
    }

    /**
     * Get a note by its hex-encoded event ID.
     *
     * @param txn The transaction
     * @param eventIdHex 64-character hex event ID
     * @return The note, or empty if not found
     */
    public Optional<Note> getNoteById(Transaction txn, String eventIdHex) {
        return getNoteById(txn, HexUtil.decode(eventIdHex));
    }

    /**
     * Get a note by its internal key (faster for repeated lookups).
     *
     * @param txn The transaction
     * @param noteKey Internal note key
     * @return The note, or empty if not found
     */
    public Optional<Note> getNoteByKey(Transaction txn, long noteKey) {
        checkOpen();
        byte[] data = NostrdbNative.getNoteByKey(ptr, txn.ptr(), noteKey);
        return Optional.ofNullable(data).map(Note::fromBytes);
    }

    /**
     * Query for notes matching a filter.
     *
     * @param txn The transaction
     * @param filter The query filter
     * @return List of query results (note keys)
     */
    public List<QueryResult> query(Transaction txn, Filter filter) {
        return query(txn, filter, 100);
    }

    /**
     * Query for notes matching a filter with an explicit limit.
     *
     * @param txn The transaction
     * @param filter The query filter
     * @param limit Maximum number of results
     * @return List of query results (note keys)
     */
    public List<QueryResult> query(Transaction txn, Filter filter, int limit) {
        checkOpen();
        byte[] resultData = NostrdbNative.query(ptr, txn.ptr(), filter.ptr(), limit);
        return QueryResult.parseResults(resultData);
    }

    /**
     * Query for notes and fetch full note objects.
     *
     * <p>This is a convenience method that queries and then fetches each note.
     *
     * @param txn The transaction
     * @param filter The query filter
     * @param limit Maximum number of results
     * @return List of notes
     */
    public List<Note> queryNotes(Transaction txn, Filter filter, int limit) {
        List<QueryResult> results = query(txn, filter, limit);
        List<Note> notes = new ArrayList<>(results.size());

        for (QueryResult result : results) {
            getNoteByKey(txn, result.noteKey()).ifPresent(notes::add);
        }

        return notes;
    }

    /**
     * Get a profile by its 32-byte public key.
     *
     * @param txn The transaction
     * @param pubkey 32-byte public key
     * @return The profile, or empty if not found
     */
    public Optional<Profile> getProfileByPubkey(Transaction txn, byte[] pubkey) {
        checkOpen();
        if (pubkey == null || pubkey.length != 32) {
            throw new IllegalArgumentException("Pubkey must be 32 bytes");
        }
        byte[] data = NostrdbNative.getProfileByPubkey(ptr, txn.ptr(), pubkey);
        return Optional.ofNullable(data).map(Profile::fromBytes);
    }

    /**
     * Get a profile by its hex-encoded public key.
     *
     * @param txn The transaction
     * @param pubkeyHex 64-character hex public key
     * @return The profile, or empty if not found
     */
    public Optional<Profile> getProfileByPubkey(Transaction txn, String pubkeyHex) {
        return getProfileByPubkey(txn, HexUtil.decode(pubkeyHex));
    }

    /**
     * Search for profiles by name.
     *
     * @param txn The transaction
     * @param query Search query (matches name/display_name)
     * @param limit Maximum number of results
     * @return List of matching public keys
     */
    public List<byte[]> searchProfiles(Transaction txn, String query, int limit) {
        checkOpen();
        byte[] resultData = NostrdbNative.searchProfiles(ptr, txn.ptr(), query, limit);

        if (resultData == null || resultData.length < 4) {
            return List.of();
        }

        ByteBuffer buf = ByteBuffer.wrap(resultData).order(ByteOrder.LITTLE_ENDIAN);
        int count = buf.getInt();

        List<byte[]> pubkeys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] pubkey = new byte[32];
            buf.get(pubkey);
            pubkeys.add(pubkey);
        }

        return pubkeys;
    }

    /**
     * Subscribe to events matching a filter.
     *
     * @param filter The query filter
     * @return The subscription
     */
    public Subscription subscribe(Filter filter) {
        checkOpen();
        long subId = NostrdbNative.subscribe(ptr, filter.ptr());
        if (subId == 0) {
            throw new NostrdbException("Failed to create subscription");
        }
        return new Subscription(this, subId);
    }

    /**
     * Poll for new notes on a subscription.
     *
     * @param subscription The subscription
     * @param maxNotes Maximum notes to return
     * @return List of note keys
     */
    public List<Long> pollForNotes(Subscription subscription, int maxNotes) {
        checkOpen();
        byte[] resultData = NostrdbNative.pollForNotes(ptr, subscription.id(), maxNotes);

        if (resultData == null || resultData.length < 4) {
            return List.of();
        }

        ByteBuffer buf = ByteBuffer.wrap(resultData).order(ByteOrder.LITTLE_ENDIAN);
        int count = buf.getInt();

        List<Long> noteKeys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            noteKeys.add(buf.getLong());
        }

        return noteKeys;
    }

    /**
     * Unsubscribe from a subscription.
     *
     * @param subscription The subscription to cancel
     */
    public void unsubscribe(Subscription subscription) {
        checkOpen();
        NostrdbNative.unsubscribe(ptr, subscription.rawId());
    }

    /**
     * Get the native pointer (for internal use).
     */
    long ptr() {
        checkOpen();
        return ptr;
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Ndb is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            NostrdbNative.ndbClose(ptr);
        }
    }
}
