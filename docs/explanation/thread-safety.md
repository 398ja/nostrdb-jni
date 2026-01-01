# Thread Safety and Concurrency

This document explains the threading model and best practices for concurrent access.

## The LMDB Constraint

nostrdb is built on LMDB, which has a critical constraint: **only one read transaction per thread**. Violating this causes undefined behavior or crashes.

```java
// WRONG: Two transactions on same thread
try (Transaction txn1 = ndb.beginTransaction()) {
    try (Transaction txn2 = ndb.beginTransaction()) {  // CRASH!
        // ...
    }
}
```

```java
// CORRECT: One transaction per thread
try (Transaction txn = ndb.beginTransaction()) {
    // All queries in this transaction
    List<Note> notes1 = ndb.queryNotes(txn, filter1, 100);
    List<Note> notes2 = ndb.queryNotes(txn, filter2, 100);
}
```

## Thread Safety by Class

### Ndb - Thread-Safe

The database handle can be shared across threads:

```java
public class MyService {
    private final Ndb ndb;  // Shared safely

    public MyService(Path dbPath) {
        this.ndb = Ndb.open(dbPath);
    }

    // Called from multiple threads
    public List<Note> query(Filter filter) {
        try (Transaction txn = ndb.beginTransaction()) {  // Thread-local
            return ndb.queryNotes(txn, filter, 100);
        }
    }
}
```

### Transaction - Thread-Local

Transactions must not be shared between threads:

```java
// WRONG: Sharing transaction
Transaction txn = ndb.beginTransaction();
executor.submit(() -> {
    ndb.query(txn, filter);  // UNSAFE!
});
```

```java
// CORRECT: Each thread creates its own
executor.submit(() -> {
    try (Transaction txn = ndb.beginTransaction()) {
        ndb.query(txn, filter);  // Safe
    }
});
```

### Filter - Not Thread-Safe

Filters are mutable during building and should not be shared:

```java
// WRONG: Sharing filter builder
Filter.Builder builder = Filter.builder();
executor.submit(() -> builder.kinds(1));  // RACE!
executor.submit(() -> builder.limit(10)); // RACE!
```

```java
// CORRECT: Build filter per-use or share immutable
try (Filter filter = Filter.builder().kinds(1).build()) {
    // Use filter in one thread only
}
```

### Note and Profile - Thread-Safe

Data objects are immutable and safe to share:

```java
Note note = ndb.getNoteById(txn, id).orElseThrow();
// note can be passed to any thread safely
executor.submit(() -> process(note));  // Safe
```

## Common Patterns

### Web Application Pattern

Each HTTP request gets its own transaction:

```java
@RestController
public class NoteController {
    private final Ndb ndb;  // Injected, shared

    @GetMapping("/notes/{id}")
    public NoteDto getNote(@PathVariable String id) {
        // Request thread gets its own transaction
        try (Transaction txn = ndb.beginTransaction()) {
            return ndb.getNoteById(txn, id)
                .map(this::toDto)
                .orElseThrow(NotFoundException::new);
        }
    }
}
```

### Thread Pool Pattern

Worker threads each create their own transaction:

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
Ndb ndb = Ndb.open(dbPath);

List<Future<List<Note>>> futures = pubkeys.stream()
    .map(pubkey -> executor.submit(() -> {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .authors(pubkey)
                 .build()) {
            return ndb.queryNotes(txn, filter, 100);
        }
    }))
    .toList();
```

### Background Subscription Pattern

Subscription polling runs in dedicated thread:

```java
public class EventPoller implements Runnable {
    private final Ndb ndb;
    private final Subscription subscription;

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            List<Long> keys = subscription.poll(100);

            if (!keys.isEmpty()) {
                // New transaction for each batch
                try (Transaction txn = ndb.beginTransaction()) {
                    for (Long key : keys) {
                        ndb.getNoteByKey(txn, key).ifPresent(this::handle);
                    }
                }
            }

            Thread.sleep(1000);
        }
    }
}
```

## Performance Considerations

### Transaction Lifetime

Keep transactions short-lived:

```java
// GOOD: Short transaction
try (Transaction txn = ndb.beginTransaction()) {
    List<Note> notes = ndb.queryNotes(txn, filter, 100);
}
// Transaction closed, do processing outside

// BAD: Long-held transaction
try (Transaction txn = ndb.beginTransaction()) {
    List<Note> notes = ndb.queryNotes(txn, filter, 100);
    slowNetworkCall(notes);  // Holds transaction too long
}
```

### Read Concurrency

LMDB supports excellent read concurrency:

- Multiple threads can read simultaneously
- Reads don't block writes
- Reads see consistent snapshot

### Write Serialization

LMDB has a single writer:

- `processEvent()` calls are serialized
- Batch with `processEvents()` for efficiency
- Writes don't block reads

## Debugging Thread Issues

### Detecting Transaction Leaks

```java
// Add logging to track transaction lifecycle
public Transaction beginTransaction() {
    Transaction txn = ndb.beginTransaction();
    logger.debug("Transaction opened on thread: {}", Thread.currentThread().getName());
    return txn;
}
```

### Common Symptoms

| Symptom | Likely Cause |
|---------|--------------|
| Crash on beginTransaction | Nested transaction on same thread |
| Hang on query | Transaction held by another operation |
| Memory growth | Unclosed transactions |

### Prevention

1. Always use try-with-resources
2. Never store transactions in fields
3. Never pass transactions between threads
4. Keep transaction scope minimal
