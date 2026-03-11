package xyz.tcheeric.nostrdb.inspector.handlers;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import xyz.tcheeric.nostrdb.Ndb;
import xyz.tcheeric.nostrdb.NdbStat;
import xyz.tcheeric.nostrdb.inspector.model.DashboardModel;
import xyz.tcheeric.nostrdb.inspector.model.KindStat;
import xyz.tcheeric.nostrdb.inspector.util.Formatting;
import xyz.tcheeric.nostrdb.inspector.util.KindLabels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for the dashboard page (GET /).
 */
public class DashboardHandler implements Handler {

    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;

    public DashboardHandler(AtomicReference<Ndb> ndbRef, ReadWriteLock rwLock) {
        this.ndbRef = ndbRef;
        this.rwLock = rwLock;
    }

    @Override
    public void handle(Context ctx) {
        rwLock.readLock().lock();
        try {
            Ndb ndb = ndbRef.get();
            DashboardModel model = buildDashboardModel(ndb);
            ctx.render("pages/dashboard.jte", Map.of("model", model));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static DashboardModel buildDashboardModel(Ndb ndb) {
        NdbStat stats = ndb.getStats();
        long fileSize = ndb.getDbFileSize();
        int subCount = ndb.getSubscriptionCount();

        // Kind stats
        List<KindStat> kindStats = new ArrayList<>();
        if (stats.commonKinds() != null) {
            for (int i = 0; i < stats.commonKinds().size(); i++) {
                NdbStat.DbCounts kind = stats.commonKinds().get(i);
                if (kind.count() > 0) {
                    kindStats.add(new KindStat(
                            i,
                            KindLabels.getLabel(i),
                            kind.count(),
                            Formatting.formatSize(kind.keySize() + kind.valueSize())
                    ));
                }
            }
        }

        // DB stats
        List<DashboardModel.DbStat> dbStats = new ArrayList<>();
        if (stats.dbs() != null) {
            for (int i = 0; i < stats.dbs().size(); i++) {
                NdbStat.DbCounts db = stats.dbs().get(i);
                dbStats.add(new DashboardModel.DbStat(
                        i,
                        db.count(),
                        Formatting.formatSize(db.keySize()),
                        Formatting.formatSize(db.valueSize())
                ));
            }
        }

        return new DashboardModel(
                ndb.getDbPath(),
                Formatting.formatSize(fileSize),
                subCount,
                stats.totalEventCount(),
                Formatting.formatSize(stats.totalDataSize()),
                kindStats,
                dbStats
        );
    }
}
