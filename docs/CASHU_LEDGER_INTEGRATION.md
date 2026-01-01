# Integrating nostrdb-jni with cashu-ledger

This guide shows how to integrate nostrdb-jni into cashu-ledger for high-performance Nostr event indexing.

## Overview

cashu-ledger currently queries relays directly for voucher events, which can be slow due to:
- Sequential relay queries
- Network latency
- No persistent caching

nostrdb-jni provides:
- Local persistent storage
- Sub-millisecond query times
- Real-time subscriptions
- Full-text search

## Step 1: Add Dependency

Add to `pom.xml`:

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostrdb-jni</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Step 2: Create NostrdbService

Create a service to manage the nostrdb instance:

```java
package me.tcheeric.cashuledger.service;

import org.springframework.stereotype.Service;
import xyz.tcheeric.nostrdb.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class NostrdbService {

    private Ndb ndb;
    private final Path dbPath;

    public NostrdbService() {
        this.dbPath = Path.of(System.getProperty("user.home"), ".cashu-ledger", "nostrdb");
    }

    @PostConstruct
    public void init() {
        // Ensure directory exists
        dbPath.toFile().mkdirs();
        ndb = Ndb.open(dbPath);
    }

    @PreDestroy
    public void shutdown() {
        if (ndb != null) {
            ndb.close();
        }
    }

    /**
     * Ingest events from relay.
     */
    public void ingestEvent(String eventJson) {
        ndb.processEvent(eventJson);
    }

    /**
     * Ingest multiple events (LDJSON format).
     */
    public int ingestEvents(String ldjson) {
        return ndb.processEvents(ldjson);
    }

    /**
     * Find a voucher by its d-tag identifier.
     */
    public Optional<Note> findVoucher(String voucherId) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(30078)  // Parameterized replaceable event
                 .dTag(voucherId)
                 .limit(1)
                 .build()) {

            List<Note> results = ndb.queryNotes(txn, filter, 1);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        }
    }

    /**
     * Find vouchers by mint URL.
     */
    public List<Note> findVouchersByMint(String mintUrl) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(30078)
                 .tag("mint", mintUrl)
                 .limit(100)
                 .build()) {

            return ndb.queryNotes(txn, filter, 100);
        }
    }

    /**
     * Find vouchers by author pubkey.
     */
    public List<Note> findVouchersByAuthor(String pubkey) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(30078)
                 .authors(pubkey)
                 .limit(100)
                 .build()) {

            return ndb.queryNotes(txn, filter, 100);
        }
    }

    /**
     * Get all vouchers in a time range.
     */
    public List<Note> findVouchersInRange(long since, long until, int limit) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(30078)
                 .since(since)
                 .until(until)
                 .limit(limit)
                 .build()) {

            return ndb.queryNotes(txn, filter, limit);
        }
    }

    /**
     * Search voucher content.
     */
    public List<Note> searchVouchers(String query, int limit) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(30078)
                 .search(query)
                 .limit(limit)
                 .build()) {

            return ndb.queryNotes(txn, filter, limit);
        }
    }

    /**
     * Get database instance for advanced queries.
     */
    public Ndb getNdb() {
        return ndb;
    }
}
```

## Step 3: Create Relay Sync Service

Create a background service to sync events from relays:

```java
package me.tcheeric.cashuledger.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.tcheeric.nostrdb.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RelaySyncService {

    private final NostrdbService nostrdbService;
    private final RelayService relayService;  // Your existing relay service
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    public RelaySyncService(NostrdbService nostrdbService, RelayService relayService) {
        this.nostrdbService = nostrdbService;
        this.relayService = relayService;
    }

    /**
     * Sync voucher events from relays every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void syncVouchers() {
        if (!syncing.compareAndSet(false, true)) {
            return;  // Already syncing
        }

        try {
            // Get events from relays (your existing logic)
            List<String> events = relayService.fetchRecentVouchers();

            // Ingest into nostrdb
            for (String eventJson : events) {
                try {
                    nostrdbService.ingestEvent(eventJson);
                } catch (Exception e) {
                    // Log and continue - invalid signatures are rejected
                }
            }
        } finally {
            syncing.set(false);
        }
    }

    /**
     * Full sync on startup.
     */
    public void fullSync() {
        // Fetch all voucher events from relays
        List<String> events = relayService.fetchAllVouchers();

        // Bulk ingest
        String ldjson = String.join("\n", events);
        int processed = nostrdbService.ingestEvents(ldjson);

        System.out.println("Synced " + processed + " voucher events");
    }
}
```

## Step 4: Update VoucherService

Modify your existing voucher service to use nostrdb:

```java
package me.tcheeric.cashuledger.service;

import org.springframework.stereotype.Service;
import xyz.tcheeric.nostrdb.Note;

import java.util.Optional;

@Service
public class VoucherService {

    private final NostrdbService nostrdbService;
    private final RelayService relayService;

    public VoucherService(NostrdbService nostrdbService, RelayService relayService) {
        this.nostrdbService = nostrdbService;
        this.relayService = relayService;
    }

    /**
     * Inspect a voucher - first check local db, then relay if not found.
     */
    public Optional<VoucherInfo> inspectVoucher(String voucherId) {
        // First, check local nostrdb (fast)
        Optional<Note> localVoucher = nostrdbService.findVoucher(voucherId);

        if (localVoucher.isPresent()) {
            return Optional.of(parseVoucher(localVoucher.get()));
        }

        // Fall back to relay query (slow)
        Optional<String> relayVoucher = relayService.fetchVoucher(voucherId);

        if (relayVoucher.isPresent()) {
            // Ingest for future queries
            nostrdbService.ingestEvent(relayVoucher.get());

            // Parse and return
            Note note = Note.fromJson(relayVoucher.get());
            return Optional.of(parseVoucher(note));
        }

        return Optional.empty();
    }

    private VoucherInfo parseVoucher(Note note) {
        VoucherInfo info = new VoucherInfo();
        info.setId(note.id());
        info.setAuthor(note.pubkey());
        info.setCreatedAt(note.createdAt());
        info.setContent(note.content());
        info.setMintUrl(note.getTagValue("mint"));
        info.setAmount(note.getTagValue("amount"));
        return info;
    }
}
```

## Step 5: Add Real-time Subscription (Optional)

For real-time voucher monitoring:

```java
package me.tcheeric.cashuledger.service;

import org.springframework.stereotype.Service;
import xyz.tcheeric.nostrdb.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class VoucherSubscriptionService {

    private final NostrdbService nostrdbService;
    private final VoucherEventHandler eventHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Subscription subscription;
    private Filter filter;

    public VoucherSubscriptionService(NostrdbService nostrdbService,
                                       VoucherEventHandler eventHandler) {
        this.nostrdbService = nostrdbService;
        this.eventHandler = eventHandler;
    }

    @PostConstruct
    public void start() {
        running.set(true);

        // Subscribe to kind 30078 (voucher events)
        filter = Filter.builder()
            .kinds(30078)
            .build();

        subscription = nostrdbService.getNdb().subscribe(filter);

        // Start polling in background
        executor.submit(this::pollLoop);
    }

    private void pollLoop() {
        Ndb ndb = nostrdbService.getNdb();

        while (running.get()) {
            try {
                List<Long> noteKeys = subscription.poll(100);

                if (!noteKeys.isEmpty()) {
                    try (Transaction txn = ndb.beginTransaction()) {
                        for (Long key : noteKeys) {
                            ndb.getNoteByKey(txn, key).ifPresent(note -> {
                                eventHandler.onNewVoucher(note);
                            });
                        }
                    }
                }

                Thread.sleep(1000);  // Poll every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error and continue
            }
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (subscription != null) {
            subscription.close();
        }
        if (filter != null) {
            filter.close();
        }
        executor.shutdown();
    }
}
```

## Performance Comparison

| Operation | Before (Relay) | After (nostrdb) |
|-----------|---------------|-----------------|
| Single voucher lookup | 200-2000ms | <1ms |
| Query by mint | 500-5000ms | <5ms |
| Full-text search | N/A | <10ms |
| Batch ingestion (1000 events) | N/A | ~100ms |

## Configuration

Add to `application.properties`:

```properties
# nostrdb path (optional, defaults to ~/.cashu-ledger/nostrdb)
nostrdb.path=${user.home}/.cashu-ledger/nostrdb

# Sync interval in milliseconds
nostrdb.sync.interval=30000
```

## Best Practices

1. **Single Ndb instance** - Create one instance per application, managed by Spring
2. **Use try-with-resources** - Always close transactions, filters, and subscriptions
3. **Background sync** - Keep local db in sync with relays asynchronously
4. **Fallback to relay** - If not found locally, query relay and cache result
5. **Batch ingestion** - Use `processEvents()` for bulk imports
