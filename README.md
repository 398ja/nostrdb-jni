# nostrdb-jni

High-performance Java bindings for [nostrdb](https://github.com/damus-io/nostrdb) - an embedded Nostr event database backed by LMDB.

## Features

- **Sub-millisecond queries** - LMDB-backed storage with zero-copy reads
- **Full Nostr support** - Filter by kinds, authors, tags, time ranges
- **Real-time subscriptions** - Poll for new events matching filters
- **Full-text search** - Search event content
- **Thread-safe** - Concurrent access with per-thread transactions

## Quick Start

```java
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

## Documentation

This documentation follows the [Diátaxis](https://diataxis.fr/) framework.

### Tutorials

*Learning-oriented lessons that guide you through a complete project.*

- [Getting Started](docs/tutorials/getting-started.md) - Install, configure, and run your first queries

### How-to Guides

*Task-oriented guides that solve specific problems.*

- [Build from Source](docs/how-to/build-from-source.md) - Compile the native and Java libraries
- [Query Events](docs/how-to/query-events.md) - Filter events by kind, author, tags, and time
- [Subscribe to Events](docs/how-to/subscribe-events.md) - Receive real-time notifications
- [Integrate with Spring Boot](docs/how-to/integrate-spring-boot.md) - Use in Spring applications

### Reference

*Technical descriptions of the API.*

- [API Reference](docs/reference/api.md) - Complete class and method documentation

### Explanation

*Background and conceptual information.*

- [Architecture](docs/explanation/architecture.md) - How nostrdb-jni works internally
- [Thread Safety](docs/explanation/thread-safety.md) - Concurrency model and best practices

## Installation

### Maven

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostrdb-jni</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Requirements

- Java 21+
- Linux, macOS, or Windows

## Examples

### Query by Author

```java
try (Transaction txn = ndb.beginTransaction();
     Filter filter = Filter.builder()
         .kinds(1)
         .authors("32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245")
         .limit(50)
         .build()) {

    List<Note> notes = ndb.queryNotes(txn, filter, 50);
}
```

### Query by Tag

```java
try (Transaction txn = ndb.beginTransaction();
     Filter filter = Filter.builder()
         .kinds(30078)
         .dTag("voucher-id")
         .limit(1)
         .build()) {

    List<Note> vouchers = ndb.queryNotes(txn, filter, 1);
}
```

### Look Up Profile

```java
try (Transaction txn = ndb.beginTransaction()) {
    Optional<Profile> profile = ndb.getProfileByPubkey(txn, pubkeyHex);

    profile.ifPresent(p -> {
        System.out.println("Name: " + p.bestDisplayName());
        System.out.println("NIP-05: " + p.nip05());
    });
}
```

### Real-time Subscription

```java
try (Filter filter = Filter.builder().kinds(1).build();
     Subscription sub = ndb.subscribe(filter)) {

    while (running) {
        List<Long> keys = sub.poll(100);
        // Process new events...
        Thread.sleep(1000);
    }
}
```

## Project Structure

```
nostrdb-jni/
├── nostrdb-jni-native/     # Rust JNI bindings
│   └── src/
│       ├── lib.rs          # JNI exports
│       ├── error.rs        # Error handling
│       └── util.rs         # Utilities
├── nostrdb-jni-java/       # Java library
│   └── src/main/java/xyz/tcheeric/nostrdb/
│       ├── Ndb.java        # Main database class
│       ├── Transaction.java
│       ├── Filter.java     # Query builder
│       ├── Note.java       # Event object
│       ├── Profile.java    # Profile object
│       └── ...
└── docs/                   # Documentation
    ├── tutorials/
    ├── how-to/
    ├── reference/
    └── explanation/
```

## License

MIT License

## Acknowledgments

- [nostrdb](https://github.com/damus-io/nostrdb) by William Casarin (jb55)
- [LMDB](https://www.symas.com/lmdb) - Lightning Memory-Mapped Database
