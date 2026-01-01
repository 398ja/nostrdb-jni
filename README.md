# nostrdb-jni

High-performance Java bindings for [nostrdb](https://github.com/damus-io/nostrdb) - an embedded Nostr event database backed by LMDB.

## Features

- **Zero-copy architecture** - Direct memory access via JNI for maximum performance
- **Full query support** - Filter by kinds, authors, tags, time ranges, and full-text search
- **Real-time subscriptions** - Poll for new events matching filters
- **Profile lookup** - Fast profile retrieval and search by name
- **Thread-safe** - Concurrent access with per-thread transactions (LMDB constraint)

## Requirements

- Java 21+
- Rust toolchain (for building native library)
- Linux, macOS, or Windows

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostrdb-jni</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```java
import xyz.tcheeric.nostrdb.*;

// Open database
try (Ndb ndb = Ndb.open(Path.of("~/.nostrdb"))) {

    // Ingest events
    ndb.processEvent(eventJson);

    // Query notes
    try (Transaction txn = ndb.beginTransaction();
         Filter filter = Filter.builder()
             .kinds(1)
             .limit(100)
             .build()) {

        List<Note> notes = ndb.queryNotes(txn, filter, 100);
        notes.forEach(note -> System.out.println(note.content()));
    }
}
```

## API Reference

### Ndb - Main Database Class

```java
// Open database
Ndb ndb = Ndb.open(Path.of("/path/to/db"));
Ndb ndb = Ndb.open("/path/to/db");

// Ingest events
ndb.processEvent(jsonString);           // Single event
ndb.processEvents(ldjsonString);        // Multiple events (newline-delimited)

// Transactions (required for all reads)
try (Transaction txn = ndb.beginTransaction()) {
    // ... queries here
}

// Close when done
ndb.close();
```

### Filter - Query Builder

```java
Filter filter = Filter.builder()
    .kinds(1, 6, 7)                    // Event kinds
    .authors("pubkey1", "pubkey2")     // Author pubkeys (hex)
    .since(1700000000L)                // After timestamp
    .until(1700100000L)                // Before timestamp
    .limit(100)                        // Max results
    .search("hello world")             // Full-text search
    .pTag("mentionedPubkey")           // p tag filter
    .eTag("referencedEventId")         // e tag filter
    .dTag("identifier")                // d tag filter
    .tag("custom", "value1", "value2") // Custom tag filter
    .build();
```

### Querying Notes

```java
try (Transaction txn = ndb.beginTransaction();
     Filter filter = Filter.builder().kinds(1).build()) {

    // Get note keys only (faster)
    List<QueryResult> results = ndb.query(txn, filter);

    // Get full notes
    List<Note> notes = ndb.queryNotes(txn, filter, 100);

    // Get note by ID
    Optional<Note> note = ndb.getNoteById(txn, "eventIdHex");
    Optional<Note> note = ndb.getNoteById(txn, eventIdBytes);

    // Get note by internal key (fastest for repeated lookups)
    Optional<Note> note = ndb.getNoteByKey(txn, noteKey);
}
```

### Note - Event Object

```java
Note note = ndb.getNoteById(txn, eventId).orElseThrow();

note.id();           // Event ID (hex)
note.idBytes();      // Event ID (bytes)
note.pubkey();       // Author pubkey (hex)
note.pubkeyBytes();  // Author pubkey (bytes)
note.createdAt();    // Unix timestamp
note.kind();         // Event kind
note.content();      // Event content
note.tags();         // All tags
note.sig();          // Signature (hex)

// Tag helpers
note.getTagValue("p");      // First p tag value
note.getTagValues("e");     // All e tag values
```

### Profiles

```java
try (Transaction txn = ndb.beginTransaction()) {
    // Lookup by pubkey
    Optional<Profile> profile = ndb.getProfileByPubkey(txn, "pubkeyHex");

    profile.ifPresent(p -> {
        p.name();              // Username
        p.displayName();       // Display name
        p.bestDisplayName();   // display_name or name
        p.about();             // Bio
        p.picture();           // Avatar URL
        p.banner();            // Banner URL
        p.nip05();             // NIP-05 identifier
        p.lud16();             // Lightning address
        p.website();           // Website URL
    });

    // Search profiles by name
    List<byte[]> pubkeys = ndb.searchProfiles(txn, "will", 10);
}
```

### Subscriptions (Real-time)

```java
try (Filter filter = Filter.builder().kinds(1).build();
     Subscription sub = ndb.subscribe(filter)) {

    while (running) {
        List<Long> noteKeys = sub.poll(100);

        if (!noteKeys.isEmpty()) {
            try (Transaction txn = ndb.beginTransaction()) {
                for (Long key : noteKeys) {
                    ndb.getNoteByKey(txn, key).ifPresent(note ->
                        System.out.println("New note: " + note.content())
                    );
                }
            }
        }

        Thread.sleep(1000);
    }
}
```

## Use Case Examples

### Cashu Voucher Inspection (cashu-ledger)

```java
public Optional<Note> findVoucher(Ndb ndb, String voucherId) {
    try (Transaction txn = ndb.beginTransaction();
         Filter filter = Filter.builder()
             .kinds(30078)
             .dTag(voucherId)
             .limit(1)
             .build()) {

        List<Note> vouchers = ndb.queryNotes(txn, filter, 1);
        return vouchers.isEmpty() ? Optional.empty() : Optional.of(vouchers.get(0));
    }
}
```

### Wallet Snapshots (cashu-client)

```java
public List<Note> getWalletSnapshots(Ndb ndb, String pubkey) {
    try (Transaction txn = ndb.beginTransaction();
         Filter filter = Filter.builder()
             .kinds(37375, 37376)
             .authors(pubkey)
             .limit(100)
             .build()) {

        return ndb.queryNotes(txn, filter, 100);
    }
}
```

### NIP-05 Lookup (bottin)

```java
public Optional<Profile> lookupProfile(Ndb ndb, String pubkey) {
    try (Transaction txn = ndb.beginTransaction()) {
        return ndb.getProfileByPubkey(txn, pubkey);
    }
}

public List<Profile> searchProfiles(Ndb ndb, String query) {
    try (Transaction txn = ndb.beginTransaction()) {
        List<byte[]> pubkeys = ndb.searchProfiles(txn, query, 20);
        return pubkeys.stream()
            .map(pk -> ndb.getProfileByPubkey(txn, pk))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }
}
```

## Building from Source

### Prerequisites

```bash
# Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Install Java 21+
# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# macOS
brew install openjdk@21
```

### Build Native Library

```bash
cd nostrdb-jni-native
cargo build --release
```

### Build Java Library

```bash
cd nostrdb-jni-java
mvn clean package
```

### Install to Local Maven Repository

```bash
cd nostrdb-jni-java
mvn install
```

## Project Structure

```
nostrdb-jni/
├── nostrdb-jni-native/     # Rust JNI bindings
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs          # JNI function exports
│       ├── error.rs        # Error handling
│       └── util.rs         # Helper utilities
├── nostrdb-jni-java/       # Java library
│   ├── pom.xml
│   └── src/
│       ├── main/java/xyz/tcheeric/nostrdb/
│       │   ├── Ndb.java            # Main database class
│       │   ├── Transaction.java    # Read transaction
│       │   ├── Filter.java         # Query filter builder
│       │   ├── Note.java           # Event data object
│       │   ├── Profile.java        # Profile data object
│       │   ├── QueryResult.java    # Query result
│       │   ├── Subscription.java   # Real-time subscription
│       │   ├── NostrdbNative.java  # JNI declarations
│       │   ├── NativeLoader.java   # Native library loader
│       │   ├── HexUtil.java        # Hex utilities
│       │   └── NostrdbException.java
│       └── test/java/xyz/tcheeric/nostrdb/
│           ├── NdbIntegrationTest.java
│           └── ExampleUsage.java
└── docs/
    └── OPTION_C_JNI_ANALYSIS.md    # Design document
```

## Thread Safety

- `Ndb` instances are thread-safe for concurrent access
- Each thread MUST use its own `Transaction` (LMDB constraint)
- `Filter` and `Subscription` instances are NOT thread-safe

```java
// Correct: Each thread gets its own transaction
ExecutorService executor = Executors.newFixedThreadPool(4);
Ndb ndb = Ndb.open(dbPath);

executor.submit(() -> {
    try (Transaction txn = ndb.beginTransaction()) {
        // Thread-local transaction
    }
});
```

## Performance Tips

1. **Reuse the Ndb instance** - Opening is expensive, keep one instance per application
2. **Use note keys for repeated lookups** - `getNoteByKey()` is faster than `getNoteById()`
3. **Batch event ingestion** - Use `processEvents()` with LDJSON for bulk imports
4. **Close resources promptly** - Use try-with-resources for transactions, filters, subscriptions

## License

MIT License - See LICENSE file for details.

## Acknowledgments

- [nostrdb](https://github.com/damus-io/nostrdb) by William Casarin (jb55)
- [LMDB](https://www.symas.com/lmdb) - Lightning Memory-Mapped Database
