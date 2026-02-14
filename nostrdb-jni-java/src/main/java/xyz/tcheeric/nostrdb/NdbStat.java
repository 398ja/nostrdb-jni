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
