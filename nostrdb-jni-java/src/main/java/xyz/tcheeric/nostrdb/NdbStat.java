package xyz.tcheeric.nostrdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Statistics from the nostrdb LMDB database.
 * <p>
 * Contains counts for each of the 16 LMDB sub-databases,
 * 15 common event kinds, and an aggregate for other kinds.
 * </p>
 *
 * @param dbs         Statistics for each of the 16 LMDB databases
 * @param commonKinds Statistics for the 15 most common event kinds
 * @param otherKinds  Aggregate statistics for all other event kinds
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NdbStat(
        @JsonProperty("dbs") List<DbCounts> dbs,
        @JsonProperty("common_kinds") List<DbCounts> commonKinds,
        @JsonProperty("other_kinds") DbCounts otherKinds
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Counts for a single LMDB database or kind category.
     *
     * @param keySize   Total size of all keys in bytes
     * @param valueSize Total size of all values in bytes
     * @param count     Number of entries
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DbCounts(
            @JsonProperty("key_size") long keySize,
            @JsonProperty("value_size") long valueSize,
            @JsonProperty("count") long count
    ) {}

    /**
     * Returns the total number of entries across all databases.
     *
     * <p>This includes the per-note index databases, so it is <em>not</em> a
     * count of stored notes — see {@link #noteCount()} for that.
     */
    public long totalEventCount() {
        long total = 0;
        if (dbs != null) {
            for (DbCounts db : dbs) {
                total += db.count();
            }
        }
        return total;
    }

    /**
     * Index of the {@code note} sub-database within {@link #dbs}.
     *
     * <p>Fixed by nostrdb's {@code enum ndb_dbs}, whose first member is
     * {@code NDB_DB_NOTE}. The order is the C enum's, NOT the alphabetical
     * order {@code mdb_stat -a} prints — reading the index off mdb_stat output
     * gives 1 (ndb_meta) and a gauge that reports zero.
     */
    private static final int NOTE_DB_INDEX = 0;

    /**
     * Returns the number of stored notes.
     *
     * <p>This is the count most callers mean by "how many events are cached".
     * It is deliberately distinct from {@link #totalEventCount()}, which sums
     * every sub-database including the per-note indexes and so reports roughly
     * six times this figure — a number that looks like an event count, is not
     * one, and has no obvious tell when read off a dashboard.
     *
     * @return the number of notes, or 0 when stats are unavailable
     */
    public long noteCount() {
        if (dbs == null || dbs.size() <= NOTE_DB_INDEX) {
            return 0;
        }
        return dbs.get(NOTE_DB_INDEX).count();
    }

    /**
     * Returns the total data size (keys + values) across all databases in bytes.
     */
    public long totalDataSize() {
        long total = 0;
        if (dbs != null) {
            for (DbCounts db : dbs) {
                total += db.keySize() + db.valueSize();
            }
        }
        return total;
    }

    /**
     * Deserializes an NdbStat from JSON bytes returned by the native layer.
     *
     * @param json JSON bytes
     * @return the deserialized NdbStat
     * @throws NostrdbException if deserialization fails
     */
    static NdbStat fromBytes(byte[] json) {
        try {
            return MAPPER.readValue(json, NdbStat.class);
        } catch (Exception e) {
            throw new NostrdbException("Failed to deserialize NdbStat", e);
        }
    }
}
