package xyz.tcheeric.nostrdb;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A read transaction for nostrdb.
 *
 * <p><b>IMPORTANT:</b> LMDB only allows one transaction per thread. Always use
 * try-with-resources to ensure proper cleanup.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (Transaction txn = ndb.beginTransaction()) {
 *     Optional<Note> note = ndb.getNoteById(txn, eventIdHex);
 *     note.ifPresent(n -> System.out.println(n.content()));
 * }
 * }</pre>
 */
public final class Transaction implements Closeable {

    private final Ndb ndb;
    private final long ptr;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    Transaction(Ndb ndb, long ptr) {
        this.ndb = ndb;
        this.ptr = ptr;
    }

    /**
     * Get the native pointer (for internal use).
     */
    long ptr() {
        checkOpen();
        return ptr;
    }

    /**
     * Check if this transaction is still open.
     *
     * @return true if open, false if closed
     */
    public boolean isOpen() {
        return !closed.get();
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Transaction is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            NostrdbNative.endTransaction(ptr);
        }
    }
}
