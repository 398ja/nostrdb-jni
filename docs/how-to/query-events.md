# How to Query Events

This guide covers different ways to query Nostr events from nostrdb.

## Basic query

All queries require a transaction and a filter:

```java
try (Transaction txn = ndb.beginTransaction();
     Filter filter = Filter.builder()
         .kinds(1)
         .limit(100)
         .build()) {

    List<Note> notes = ndb.queryNotes(txn, filter, 100);
}
```

## Filter by event kind

Query specific event types:

```java
// Text notes (kind 1)
Filter.builder().kinds(1).build();

// Profiles (kind 0)
Filter.builder().kinds(0).build();

// Multiple kinds
Filter.builder().kinds(1, 6, 7).build();

// Parameterized replaceable events (kind 30078 for vouchers)
Filter.builder().kinds(30078).build();
```

## Filter by author

Query events from specific pubkeys:

```java
// Single author
Filter.builder()
    .authors("32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245")
    .build();

// Multiple authors
Filter.builder()
    .authors("pubkey1", "pubkey2", "pubkey3")
    .build();

// Using byte arrays
byte[] pubkey = HexUtil.decode("32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245");
Filter.builder()
    .authors(pubkey)
    .build();
```

## Filter by time range

Query events within a time window:

```java
long oneHourAgo = Instant.now().minusSeconds(3600).getEpochSecond();
long now = Instant.now().getEpochSecond();

Filter.builder()
    .since(oneHourAgo)
    .until(now)
    .build();
```

## Filter by tags

Query events with specific tags:

```java
// Events mentioning a pubkey (p tag)
Filter.builder()
    .pTag("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d")
    .build();

// Events referencing another event (e tag)
Filter.builder()
    .eTag("5c83da77af1dec6d7289834998ad7aafbd9e2191396d75ec3cc27f5a77226f36")
    .build();

// Parameterized replaceable events by identifier (d tag)
Filter.builder()
    .kinds(30078)
    .dTag("my-voucher-id")
    .build();

// Custom tags
Filter.builder()
    .tag("mint", "https://mint.example.com")
    .build();
```

## Full-text search

Search event content:

```java
Filter.builder()
    .kinds(1)
    .search("bitcoin lightning")
    .limit(50)
    .build();
```

## Combine filters

Filters can combine multiple criteria:

```java
Filter.builder()
    .kinds(1)
    .authors("pubkey1", "pubkey2")
    .since(oneWeekAgo)
    .pTag("mentionedPubkey")
    .limit(100)
    .build();
```

## Get note by ID

For direct lookups, use `getNoteById`:

```java
// By hex string
Optional<Note> note = ndb.getNoteById(txn, "d7dd5eb3...");

// By byte array
byte[] eventId = HexUtil.decode("d7dd5eb3...");
Optional<Note> note = ndb.getNoteById(txn, eventId);
```

## Get note by key (faster)

For repeated lookups, use internal keys:

```java
// First, get keys from a query
List<QueryResult> results = ndb.query(txn, filter);

// Then fetch notes by key (faster than by ID)
for (QueryResult result : results) {
    Optional<Note> note = ndb.getNoteByKey(txn, result.noteKey());
}
```

## Pagination

Use `since`/`until` with the last event's timestamp:

```java
long cursor = Instant.now().getEpochSecond();

while (true) {
    try (Filter filter = Filter.builder()
            .kinds(1)
            .until(cursor)
            .limit(100)
            .build()) {

        List<Note> notes = ndb.queryNotes(txn, filter, 100);
        if (notes.isEmpty()) break;

        // Process notes...

        // Update cursor to oldest event
        cursor = notes.get(notes.size() - 1).createdAt() - 1;
    }
}
```
