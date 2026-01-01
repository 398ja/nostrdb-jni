package xyz.tcheeric.nostrdb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * A query result containing a note key.
 *
 * <p>Query results are lightweight containers that hold internal note keys.
 * Use {@link Ndb#getNoteByKey(Transaction, long)} to fetch the full note.
 */
public final class QueryResult {

    private final long noteKey;

    private QueryResult(long noteKey) {
        this.noteKey = noteKey;
    }

    /**
     * Get the internal note key.
     *
     * <p>This key can be used with {@link Ndb#getNoteByKey(Transaction, long)}
     * for efficient note retrieval.
     *
     * @return The note key
     */
    public long noteKey() {
        return noteKey;
    }

    /**
     * Parse query results from native byte array.
     *
     * <p>Format: [count:4][key1:8][key2:8]...
     */
    static List<QueryResult> parseResults(byte[] data) {
        if (data == null || data.length < 4) {
            return List.of();
        }

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int count = buf.getInt();

        List<QueryResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(new QueryResult(buf.getLong()));
        }

        return results;
    }

    @Override
    public String toString() {
        return "QueryResult{noteKey=" + noteKey + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryResult that = (QueryResult) o;
        return noteKey == that.noteKey;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(noteKey);
    }
}
