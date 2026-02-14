# Monitor Database

This guide shows how to use the observability APIs to monitor your nostrdb instance.

## Prerequisites

- A running `Ndb` instance (see [Getting Started](../tutorials/getting-started.md))

## Get Database Statistics

Use `getStats()` to retrieve detailed LMDB statistics including record counts and storage sizes across all sub-databases.

```java
try (Ndb ndb = Ndb.open(Path.of("/path/to/db"))) {
    NdbStat stats = ndb.getStats();

    // Aggregate counts
    System.out.println("Total entries: " + stats.totalEventCount());
    System.out.println("Total data size: " + stats.totalDataSize() + " bytes");

    // Per-database breakdown
    for (int i = 0; i < stats.dbs().size(); i++) {
        NdbStat.DbCounts db = stats.dbs().get(i);
        System.out.printf("DB %d: %d entries, %d bytes keys, %d bytes values%n",
                i, db.count(), db.keySize(), db.valueSize());
    }

    // Per-kind breakdown
    for (NdbStat.DbCounts kind : stats.commonKinds()) {
        System.out.printf("Kind: %d entries, %d bytes%n",
                kind.count(), kind.keySize() + kind.valueSize());
    }

    // Other kinds aggregate
    NdbStat.DbCounts other = stats.otherKinds();
    System.out.printf("Other kinds: %d entries%n", other.count());
}
```

## Track Active Subscriptions

Monitor how many subscriptions are currently active:

```java
int activeSubs = ndb.getSubscriptionCount();
System.out.println("Active subscriptions: " + activeSubs);
```

This is useful for detecting subscription leaks where `close()` is not being called.

## Monitor Database File Size

Track the LMDB data file size to monitor disk usage growth:

```java
long fileSize = ndb.getDbFileSize();
if (fileSize >= 0) {
    System.out.printf("DB file size: %.2f MB%n", fileSize / (1024.0 * 1024.0));
} else {
    System.out.println("Could not read DB file size");
}
```

## Periodic Health Check

Combine all metrics into a periodic health check:

```java
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

scheduler.scheduleAtFixedRate(() -> {
    if (!ndb.isOpen()) return;

    NdbStat stats = ndb.getStats();
    long fileSizeMb = ndb.getDbFileSize() / (1024 * 1024);
    int subs = ndb.getSubscriptionCount();

    log.info("nostrdb health: entries={}, dataSize={}MB, fileSize={}MB, subscriptions={}",
            stats.totalEventCount(),
            stats.totalDataSize() / (1024 * 1024),
            fileSizeMb,
            subs);
}, 0, 60, TimeUnit.SECONDS);
```

## Exposing Metrics

For production deployments, expose these stats through your monitoring framework. For example, with Micrometer:

```java
Gauge.builder("nostrdb.entries.total", ndb, db -> db.getStats().totalEventCount())
        .register(meterRegistry);
Gauge.builder("nostrdb.file.size.bytes", ndb, Ndb::getDbFileSize)
        .register(meterRegistry);
Gauge.builder("nostrdb.subscriptions.active", ndb, Ndb::getSubscriptionCount)
        .register(meterRegistry);
```
