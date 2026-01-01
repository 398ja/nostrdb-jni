package xyz.tcheeric.nostrdb;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A subscription to nostrdb events.
 *
 * <p>Subscriptions allow receiving real-time updates when new notes matching
 * the filter are added to the database.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (Filter filter = Filter.builder().kinds(1).build();
 *      Subscription sub = ndb.subscribe(filter)) {
 *
 *     // Poll for new notes periodically
 *     List<Long> noteKeys = ndb.pollForNotes(sub, 100);
 *     try (Transaction txn = ndb.beginTransaction()) {
 *         for (long key : noteKeys) {
 *             ndb.getNoteByKey(txn, key).ifPresent(note ->
 *                 System.out.println(note.content())
 *             );
 *         }
 *     }
 * }
 * }</pre>
 */
public final class Subscription implements Closeable {

    private final Ndb ndb;
    private final long id;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    Subscription(Ndb ndb, long id) {
        this.ndb = ndb;
        this.id = id;
    }

    /**
     * Get the subscription ID (throws if closed).
     */
    long id() {
        checkOpen();
        return id;
    }

    /**
     * Get the raw subscription ID (for internal use during close).
     */
    long rawId() {
        return id;
    }

    /**
     * Poll for new notes on this subscription.
     *
     * @param maxNotes Maximum number of notes to return
     * @return List of note keys
     */
    public List<Long> poll(int maxNotes) {
        checkOpen();
        return ndb.pollForNotes(this, maxNotes);
    }

    /**
     * Poll for new notes with default limit of 100.
     *
     * @return List of note keys
     */
    public List<Long> poll() {
        return poll(100);
    }

    /**
     * Check if this subscription is still active.
     *
     * @return true if active, false if closed
     */
    public boolean isActive() {
        return !closed.get();
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Subscription is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            ndb.unsubscribe(this);
        }
    }
}
