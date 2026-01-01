# Architecture

This document explains how nostrdb-jni works and the design decisions behind it.

## Overview

nostrdb-jni provides Java bindings to [nostrdb](https://github.com/damus-io/nostrdb), a high-performance embedded database for Nostr events. The architecture consists of three layers:

```
┌─────────────────────────────────────┐
│         Java Application            │
├─────────────────────────────────────┤
│      nostrdb-jni (Java API)         │
├─────────────────────────────────────┤
│      nostrdb-jni (Rust/JNI)         │
├─────────────────────────────────────┤
│           nostrdb (Rust)            │
├─────────────────────────────────────┤
│             LMDB (C)                │
└─────────────────────────────────────┘
```

## Components

### Java Library (`nostrdb-jni-java`)

The Java library provides a clean, idiomatic API:

- **Ndb** - Main database handle, manages lifecycle
- **Transaction** - Read transaction wrapper
- **Filter** - Query builder with fluent API
- **Note** - Event data object with JSON parsing
- **Profile** - User profile data object
- **Subscription** - Real-time event subscription

All classes use `Closeable` for proper resource management with try-with-resources.

### Native Library (`nostrdb-jni-native`)

The Rust native library bridges Java and nostrdb:

- Implements JNI function exports
- Manages pointer-based ownership
- Handles error translation
- Serializes data for Java consumption

### nostrdb

The underlying nostrdb library provides:

- Event storage and indexing
- Query execution
- Signature verification
- Full-text search
- Real-time subscriptions

### LMDB

LMDB (Lightning Memory-Mapped Database) provides:

- Memory-mapped I/O for zero-copy reads
- ACID transactions
- Crash-safe persistence
- High read concurrency

## Data Flow

### Event Ingestion

```
Java String → JNI → Rust String → nostrdb::process_event → LMDB
```

1. Java passes event JSON to native method
2. JNI converts Java String to Rust String
3. nostrdb parses, validates signature, and indexes
4. LMDB persists to disk

### Query Execution

```
Filter → JNI → nostrdb::query → Results → JSON → Java Note
```

1. Java builds Filter and passes pointer to native
2. nostrdb executes query against LMDB indices
3. Results serialized as JSON
4. Java parses JSON into Note objects

## Memory Model

### Pointer Management

Native objects (Ndb, Transaction, Filter) are represented as opaque pointers in Java:

```java
public final class Ndb {
    private final long ptr;  // Native pointer
}
```

The Rust side boxes objects and returns raw pointers:

```rust
#[no_mangle]
pub extern "C" fn Java_..._ndbOpen(...) -> jlong {
    let ndb = Ndb::new(...);
    Box::into_raw(Box::new(ndb)) as jlong
}
```

### Ownership Rules

- Java owns pointers and must call close/destroy
- Filter builder consumes old pointer on each method call
- Transactions must be closed before Ndb

### Memory Safety

- `AtomicBoolean` prevents double-close
- Null pointer checks on native side
- Box::from_raw only called once per pointer

## Threading Model

### LMDB Constraints

LMDB requires **one transaction per thread**. This is enforced by design:

```java
// Each thread creates its own transaction
ExecutorService executor = Executors.newFixedThreadPool(4);
executor.submit(() -> {
    try (Transaction txn = ndb.beginTransaction()) {
        // Thread-local transaction
    }
});
```

### Thread Safety

| Class | Thread-Safe |
|-------|-------------|
| Ndb | Yes |
| Transaction | No (one per thread) |
| Filter | No |
| Subscription | No |
| Note | Yes (immutable) |
| Profile | Yes (immutable) |

### Concurrent Access

The Ndb instance itself is thread-safe:

```java
// Safe: Multiple threads, each with own transaction
Ndb ndb = Ndb.open(path);  // Shared

Thread t1 = new Thread(() -> {
    try (Transaction txn = ndb.beginTransaction()) { ... }
});

Thread t2 = new Thread(() -> {
    try (Transaction txn = ndb.beginTransaction()) { ... }
});
```

## Performance Characteristics

### Read Performance

- **Index lookups**: O(log n) via B-tree
- **Key lookups**: Near O(1) with LMDB
- **Memory-mapped**: No kernel copies for reads

### Write Performance

- **Single writer**: LMDB limitation
- **Batching**: Use `processEvents()` for bulk
- **Signature verification**: CPU-bound

### Memory Usage

- **Minimal heap**: Data stays in native memory
- **Memory-mapped**: OS manages page cache
- **JSON parsing**: Temporary allocations

## Error Handling

### Exception Hierarchy

```
RuntimeException
└── NostrdbException
    └── (with cause for wrapped errors)
```

### Native Error Translation

Rust errors are converted to Java exceptions:

```rust
match result {
    Ok(value) => value,
    Err(e) => {
        throw_exception(env, &e.to_string());
        return_default
    }
}
```

### Validation

- Event signatures are verified by nostrdb
- Invalid events are silently rejected
- ID/pubkey length validated in Java layer

## Design Decisions

### Why JNI over JNA/JNR?

- **Performance**: JNI has lower overhead
- **Control**: Direct memory management
- **Rust integration**: jni-rs crate is mature

### Why JSON for Note/Profile?

- **Simplicity**: Avoids complex flatbuffer parsing in Java
- **Flexibility**: Easy to extend fields
- **Trade-off**: Slight performance cost, acceptable for typical use

### Why Closeable everywhere?

- **Familiar pattern**: Java developers expect it
- **try-with-resources**: Ensures cleanup
- **Explicit lifecycle**: No finalizer reliance
