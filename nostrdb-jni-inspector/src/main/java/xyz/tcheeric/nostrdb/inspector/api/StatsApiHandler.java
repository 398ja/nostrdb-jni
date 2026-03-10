package xyz.tcheeric.nostrdb.inspector.api;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import xyz.tcheeric.nostrdb.Ndb;
import xyz.tcheeric.nostrdb.NdbStat;
import xyz.tcheeric.nostrdb.inspector.util.Formatting;
import xyz.tcheeric.nostrdb.inspector.util.KindLabels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for GET /api/stats — database statistics as JSON.
 */
public class StatsApiHandler implements Handler {

    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;

    public StatsApiHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
    }

    @Override
    public void handle(Context ctx) {
        rwLock.readLock().lock();
        try {
            Ndb ndb = ndbRef.get();
            NdbStat stats = ndb.getStats();
            long fileSize = ndb.getDbFileSize();
            int subCount = ndb.getSubscriptionCount();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("dbPath", ndb.getDbPath());
            response.put("fileSize", fileSize);
            response.put("fileSizeHuman", Formatting.formatSize(fileSize));
            response.put("subscriptionCount", subCount);
            response.put("totalEntries", stats.totalEventCount());
            response.put("totalDataSize", stats.totalDataSize());
            response.put("totalDataSizeHuman", Formatting.formatSize(stats.totalDataSize()));

            // Per-DB stats
            List<Map<String, Object>> dbs = new ArrayList<>();
            if (stats.dbs() != null) {
                for (int i = 0; i < stats.dbs().size(); i++) {
                    NdbStat.DbCounts db = stats.dbs().get(i);
                    Map<String, Object> dbMap = new LinkedHashMap<>();
                    dbMap.put("index", i);
                    dbMap.put("keySize", db.keySize());
                    dbMap.put("valueSize", db.valueSize());
                    dbMap.put("count", db.count());
                    dbs.add(dbMap);
                }
            }
            response.put("dbs", dbs);

            // Common kinds
            List<Map<String, Object>> commonKinds = new ArrayList<>();
            if (stats.commonKinds() != null) {
                for (int i = 0; i < stats.commonKinds().size(); i++) {
                    NdbStat.DbCounts kind = stats.commonKinds().get(i);
                    Map<String, Object> kindMap = new LinkedHashMap<>();
                    kindMap.put("kind", i);
                    kindMap.put("label", KindLabels.getLabel(i));
                    kindMap.put("keySize", kind.keySize());
                    kindMap.put("valueSize", kind.valueSize());
                    kindMap.put("count", kind.count());
                    commonKinds.add(kindMap);
                }
            }
            response.put("commonKinds", commonKinds);

            // Other kinds
            if (stats.otherKinds() != null) {
                Map<String, Object> other = new LinkedHashMap<>();
                other.put("keySize", stats.otherKinds().keySize());
                other.put("valueSize", stats.otherKinds().valueSize());
                other.put("count", stats.otherKinds().count());
                response.put("otherKinds", other);
            }

            ctx.json(response);
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
