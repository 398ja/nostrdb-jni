# How to Subscribe to Real-time Events

This guide explains how to receive notifications when new events are added to the database.

## Create a subscription

```java
try (Filter filter = Filter.builder().kinds(1).build();
     Subscription sub = ndb.subscribe(filter)) {

    // Poll for new events
    List<Long> noteKeys = sub.poll(100);
}
```

## Poll loop pattern

Subscriptions require polling. Here's a typical pattern:

```java
try (Filter filter = Filter.builder().kinds(1).build();
     Subscription sub = ndb.subscribe(filter)) {

    while (running) {
        List<Long> noteKeys = sub.poll(100);

        if (!noteKeys.isEmpty()) {
            try (Transaction txn = ndb.beginTransaction()) {
                for (Long key : noteKeys) {
                    ndb.getNoteByKey(txn, key).ifPresent(note -> {
                        System.out.println("New note: " + note.content());
                    });
                }
            }
        }

        Thread.sleep(1000);  // Poll every second
    }
}
```

## Background thread subscription

For production applications, run the poll loop in a background thread:

```java
public class EventSubscriber {
    private final Ndb ndb;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Subscription subscription;
    private Filter filter;

    public void start() {
        running.set(true);
        filter = Filter.builder().kinds(1).build();
        subscription = ndb.subscribe(filter);
        executor.submit(this::pollLoop);
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                List<Long> noteKeys = subscription.poll(100);

                if (!noteKeys.isEmpty()) {
                    try (Transaction txn = ndb.beginTransaction()) {
                        for (Long key : noteKeys) {
                            ndb.getNoteByKey(txn, key).ifPresent(this::handleNote);
                        }
                    }
                }

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleNote(Note note) {
        // Process the new note
    }

    public void stop() {
        running.set(false);
        if (subscription != null) subscription.close();
        if (filter != null) filter.close();
        executor.shutdown();
    }
}
```

## Spring Boot integration

For Spring applications, use `@PostConstruct` and `@PreDestroy`:

```java
@Service
public class NostrEventListener {

    private final Ndb ndb;
    private Subscription subscription;
    private Filter filter;

    @PostConstruct
    public void start() {
        filter = Filter.builder().kinds(1).build();
        subscription = ndb.subscribe(filter);
        // Start polling thread...
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) subscription.close();
        if (filter != null) filter.close();
    }
}
```

## Multiple subscriptions

You can have multiple subscriptions with different filters:

```java
// Subscribe to text notes
Subscription textNotes = ndb.subscribe(
    Filter.builder().kinds(1).build()
);

// Subscribe to reactions
Subscription reactions = ndb.subscribe(
    Filter.builder().kinds(7).build()
);

// Subscribe to specific author
Subscription authorPosts = ndb.subscribe(
    Filter.builder()
        .kinds(1)
        .authors("pubkey...")
        .build()
);
```

## Resource management

Always close subscriptions and filters when done:

```java
// Using try-with-resources (recommended)
try (Filter filter = Filter.builder().kinds(1).build();
     Subscription sub = ndb.subscribe(filter)) {
    // Use subscription
}

// Manual cleanup
Filter filter = null;
Subscription sub = null;
try {
    filter = Filter.builder().kinds(1).build();
    sub = ndb.subscribe(filter);
    // Use subscription
} finally {
    if (sub != null) sub.close();
    if (filter != null) filter.close();
}
```
