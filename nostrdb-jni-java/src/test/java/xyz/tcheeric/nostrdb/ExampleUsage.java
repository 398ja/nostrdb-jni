package xyz.tcheeric.nostrdb;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Example usage of nostrdb-jni library.
 *
 * <p>This class demonstrates common patterns for using nostrdb in Java applications,
 * particularly for Nostr-based applications like cashu-ledger, cashu-client, and bottin.
 */
public class ExampleUsage {

    /**
     * Basic database operations example.
     */
    public static void basicUsage() {
        // Open database (create if not exists)
        try (Ndb ndb = Ndb.open(Path.of(System.getProperty("user.home"), ".nostrdb"))) {

            // Ingest a raw event JSON
            String eventJson = """
                {
                    "id": "...",
                    "pubkey": "...",
                    "created_at": 1700000000,
                    "kind": 1,
                    "content": "Hello Nostr!",
                    "tags": [],
                    "sig": "..."
                }
                """;
            ndb.processEvent(eventJson);

            // Query with a transaction
            try (Transaction txn = ndb.beginTransaction();
                 Filter filter = Filter.builder()
                     .kinds(1)
                     .limit(100)
                     .build()) {

                List<Note> notes = ndb.queryNotes(txn, filter, 100);
                for (Note note : notes) {
                    System.out.printf("Note %s: %s%n",
                        note.id().substring(0, 8),
                        note.content());
                }
            }
        }
    }

    /**
     * Cashu voucher inspection pattern (for cashu-ledger).
     *
     * <p>Demonstrates querying for NIP-60/61 voucher events (kind 30078).
     */
    public static void cashuVoucherPattern(Ndb ndb, String voucherId) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(30078)  // Parameterized replaceable event
                 .dTag(voucherId)
                 .limit(1)
                 .build()) {

            List<Note> vouchers = ndb.queryNotes(txn, filter, 1);

            if (!vouchers.isEmpty()) {
                Note voucher = vouchers.get(0);
                System.out.println("Found voucher: " + voucher.id());
                System.out.println("Content: " + voucher.content());

                // Extract mint URL from tags
                String mintUrl = voucher.getTagValue("mint");
                if (mintUrl != null) {
                    System.out.println("Mint: " + mintUrl);
                }
            } else {
                System.out.println("Voucher not found: " + voucherId);
            }
        }
    }

    /**
     * Wallet snapshot pattern (for cashu-client).
     *
     * <p>Demonstrates querying for NIP-60 wallet events.
     */
    public static void walletSnapshotPattern(Ndb ndb, String pubkey) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(37375, 37376)  // Wallet snapshot kinds
                 .authors(pubkey)
                 .limit(10)
                 .build()) {

            List<Note> snapshots = ndb.queryNotes(txn, filter, 10);

            for (Note snapshot : snapshots) {
                System.out.printf("Wallet snapshot %s (kind %d) at %d%n",
                    snapshot.id().substring(0, 8),
                    snapshot.kind(),
                    snapshot.createdAt());
            }
        }
    }

    /**
     * NIP-05 lookup pattern (for bottin).
     *
     * <p>Demonstrates profile lookup and search.
     */
    public static void nip05LookupPattern(Ndb ndb, String pubkey) {
        try (Transaction txn = ndb.beginTransaction()) {
            // Lookup profile by pubkey
            ndb.getProfileByPubkey(txn, pubkey).ifPresent(profile -> {
                System.out.println("Name: " + profile.name());
                System.out.println("Display: " + profile.displayName());
                System.out.println("NIP-05: " + profile.nip05());
                System.out.println("Lightning: " + profile.bestLightningAddress());
            });

            // Search profiles by name
            List<byte[]> matchingPubkeys = ndb.searchProfiles(txn, "will", 10);
            System.out.printf("Found %d profiles matching 'will'%n", matchingPubkeys.size());

            for (byte[] pk : matchingPubkeys) {
                ndb.getProfileByPubkey(txn, pk).ifPresent(p ->
                    System.out.println("  - " + p.bestDisplayName())
                );
            }
        }
    }

    /**
     * Real-time subscription pattern.
     *
     * <p>Demonstrates subscribing to new events.
     */
    public static void subscriptionPattern(Ndb ndb) throws InterruptedException {
        // Subscribe to kind 1 text notes
        try (Filter filter = Filter.builder().kinds(1).build();
             Subscription sub = ndb.subscribe(filter)) {

            System.out.println("Subscribed to text notes...");

            // Poll loop (in real app, this would be in a separate thread)
            for (int i = 0; i < 10; i++) {
                List<Long> noteKeys = sub.poll(100);

                if (!noteKeys.isEmpty()) {
                    try (Transaction txn = ndb.beginTransaction()) {
                        for (Long key : noteKeys) {
                            ndb.getNoteByKey(txn, key).ifPresent(note ->
                                System.out.printf("New note: %s%n", note.content())
                            );
                        }
                    }
                }

                Thread.sleep(1000); // Poll every second
            }
        }
    }

    /**
     * Batch event ingestion pattern.
     *
     * <p>Demonstrates efficient bulk event processing.
     */
    public static void batchIngestionPattern(Ndb ndb, List<String> eventJsons) {
        // Join events with newlines for LDJSON format
        String ldjson = String.join("\n", eventJsons);

        int processed = ndb.processEvents(ldjson);
        System.out.printf("Processed %d events%n", processed);
    }

    /**
     * Time-based query pattern.
     *
     * <p>Demonstrates querying events within a time range.
     */
    public static void timeBasedQueryPattern(Ndb ndb) {
        long oneHourAgo = Instant.now().minusSeconds(3600).getEpochSecond();
        long now = Instant.now().getEpochSecond();

        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(1)
                 .since(oneHourAgo)
                 .until(now)
                 .limit(50)
                 .build()) {

            List<Note> recentNotes = ndb.queryNotes(txn, filter, 50);
            System.out.printf("Found %d notes in the last hour%n", recentNotes.size());
        }
    }

    /**
     * Tag-based filtering pattern.
     *
     * <p>Demonstrates querying by various tag types.
     */
    public static void tagFilteringPattern(Ndb ndb, String targetPubkey) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(1)
                 .pTag(targetPubkey)  // Notes mentioning this pubkey
                 .limit(20)
                 .build()) {

            List<Note> mentions = ndb.queryNotes(txn, filter, 20);
            System.out.printf("Found %d notes mentioning %s%n",
                mentions.size(),
                targetPubkey.substring(0, 8));
        }
    }

    /**
     * Full-text search pattern.
     */
    public static void searchPattern(Ndb ndb, String searchQuery) {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(1)
                 .search(searchQuery)
                 .limit(20)
                 .build()) {

            List<Note> results = ndb.queryNotes(txn, filter, 20);
            System.out.printf("Found %d notes matching '%s'%n",
                results.size(),
                searchQuery);

            for (Note note : results) {
                System.out.println("  - " + note.content());
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("nostrdb-jni Example Usage");
        System.out.println("=========================");
        System.out.println();
        System.out.println("See the method implementations for usage patterns.");
        System.out.println();
        System.out.println("Key patterns:");
        System.out.println("  - basicUsage()           - Open, ingest, query");
        System.out.println("  - cashuVoucherPattern()  - Query kind 30078 vouchers");
        System.out.println("  - walletSnapshotPattern()- Query NIP-60 wallet events");
        System.out.println("  - nip05LookupPattern()   - Profile lookup and search");
        System.out.println("  - subscriptionPattern()  - Real-time event subscription");
        System.out.println("  - batchIngestionPattern()- Bulk LDJSON ingestion");
        System.out.println("  - timeBasedQueryPattern()- Time-range queries");
        System.out.println("  - tagFilteringPattern()  - Tag-based filtering");
        System.out.println("  - searchPattern()        - Full-text search");
    }
}
