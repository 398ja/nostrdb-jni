package xyz.tcheeric.nostrdb.inspector.model;

import java.util.List;

/**
 * View model for the dashboard page.
 *
 * @param dbPath            Database path
 * @param fileSize          Pre-formatted file size
 * @param subscriptionCount Active subscription count
 * @param totalEntries      Total entries across all DBs
 * @param totalDataSize     Pre-formatted total data size
 * @param kindStats         Per-kind statistics
 * @param dbStats           Per-sub-database statistics
 */
public record DashboardModel(
        String dbPath,
        String fileSize,
        int subscriptionCount,
        long totalEntries,
        String totalDataSize,
        List<KindStat> kindStats,
        List<DbStat> dbStats
) {
    /**
     * Statistics for a single LMDB sub-database.
     */
    public record DbStat(int index, long count, String keySize, String valueSize) {}
}
