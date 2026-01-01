# API Reference

Complete reference for all nostrdb-jni classes and methods.

## Ndb

Main database class. Thread-safe for concurrent access.

### Static Methods

#### `open(Path dbPath)`
Opens a database at the specified path.

```java
Ndb ndb = Ndb.open(Path.of("/path/to/db"));
```

**Parameters:**
- `dbPath` - Path to the database directory (created if it doesn't exist)

**Returns:** `Ndb` instance

**Throws:** `NostrdbException` if the database cannot be opened

#### `open(String dbPath)`
Opens a database at the specified path.

```java
Ndb ndb = Ndb.open("/path/to/db");
```

### Instance Methods

#### `processEvent(String json)`
Ingests a single Nostr event.

```java
ndb.processEvent(eventJsonString);
```

**Parameters:**
- `json` - JSON event string (relay format `["EVENT", {...}]` or raw `{...}`)

**Throws:** `NostrdbException` if processing fails

#### `processEvents(String ldjson)`
Ingests multiple events from newline-delimited JSON.

```java
int count = ndb.processEvents(ldjsonString);
```

**Parameters:**
- `ldjson` - Newline-delimited JSON events

**Returns:** Number of events successfully processed

#### `beginTransaction()`
Begins a read transaction. **One transaction per thread only.**

```java
try (Transaction txn = ndb.beginTransaction()) {
    // queries here
}
```

**Returns:** `Transaction` instance

**Throws:** `NostrdbException` if transaction cannot be started

#### `getNoteById(Transaction txn, byte[] eventId)`
Gets a note by its 32-byte event ID.

```java
Optional<Note> note = ndb.getNoteById(txn, eventIdBytes);
```

**Parameters:**
- `txn` - Active transaction
- `eventId` - 32-byte event ID

**Returns:** `Optional<Note>` (empty if not found)

#### `getNoteById(Transaction txn, String eventIdHex)`
Gets a note by its hex-encoded event ID.

```java
Optional<Note> note = ndb.getNoteById(txn, "d7dd5eb3...");
```

#### `getNoteByKey(Transaction txn, long noteKey)`
Gets a note by its internal key. Faster than `getNoteById` for repeated lookups.

```java
Optional<Note> note = ndb.getNoteByKey(txn, noteKey);
```

#### `query(Transaction txn, Filter filter)`
Queries for notes matching a filter. Returns keys only.

```java
List<QueryResult> results = ndb.query(txn, filter);
```

**Returns:** List of `QueryResult` containing note keys

#### `query(Transaction txn, Filter filter, int limit)`
Queries with explicit limit.

#### `queryNotes(Transaction txn, Filter filter, int limit)`
Queries and fetches full note objects.

```java
List<Note> notes = ndb.queryNotes(txn, filter, 100);
```

#### `getProfileByPubkey(Transaction txn, byte[] pubkey)`
Gets a profile by 32-byte public key.

```java
Optional<Profile> profile = ndb.getProfileByPubkey(txn, pubkeyBytes);
```

#### `getProfileByPubkey(Transaction txn, String pubkeyHex)`
Gets a profile by hex-encoded public key.

#### `searchProfiles(Transaction txn, String query, int limit)`
Searches profiles by name.

```java
List<byte[]> pubkeys = ndb.searchProfiles(txn, "will", 10);
```

**Returns:** List of matching public keys

#### `subscribe(Filter filter)`
Subscribes to events matching a filter.

```java
Subscription sub = ndb.subscribe(filter);
```

**Returns:** `Subscription` instance

#### `pollForNotes(Subscription subscription, int maxNotes)`
Polls for new notes on a subscription.

```java
List<Long> noteKeys = ndb.pollForNotes(sub, 100);
```

#### `unsubscribe(Subscription subscription)`
Cancels a subscription.

#### `close()`
Closes the database. Called automatically with try-with-resources.

---

## Transaction

Read transaction. Implements `Closeable`.

### Methods

#### `isOpen()`
Checks if the transaction is still open.

```java
boolean open = txn.isOpen();
```

#### `close()`
Ends the transaction.

---

## Filter

Query filter. Implements `Closeable`. Built using `Filter.Builder`.

### Filter.Builder

#### `builder()`
Creates a new filter builder.

```java
Filter.Builder builder = Filter.builder();
```

#### `kinds(int... kinds)`
Adds event kinds to match.

```java
builder.kinds(1, 6, 7);
```

#### `authors(String... pubkeysHex)`
Adds author public keys (hex-encoded).

```java
builder.authors("32e1827...", "3bf0c63...");
```

#### `authors(byte[]... pubkeys)`
Adds author public keys (raw bytes).

#### `tag(String tagName, String... values)`
Adds a tag filter.

```java
builder.tag("t", "bitcoin", "nostr");
```

#### `pTag(String... pubkeysHex)`
Shorthand for `tag("p", ...)`.

#### `eTag(String... eventIdsHex)`
Shorthand for `tag("e", ...)`.

#### `dTag(String... values)`
Shorthand for `tag("d", ...)`.

#### `since(long since)`
Sets minimum timestamp (inclusive).

```java
builder.since(1700000000L);
```

#### `until(long until)`
Sets maximum timestamp (inclusive).

#### `limit(int limit)`
Sets maximum results.

#### `search(String search)`
Sets full-text search query.

#### `build()`
Builds the filter.

```java
Filter filter = builder.build();
```

---

## Note

Nostr event data object. Immutable.

### Methods

#### `id()`
Returns event ID (hex-encoded).

#### `idBytes()`
Returns event ID as bytes.

#### `pubkey()`
Returns author public key (hex-encoded).

#### `pubkeyBytes()`
Returns author public key as bytes.

#### `createdAt()`
Returns creation timestamp (Unix seconds).

#### `kind()`
Returns event kind.

#### `content()`
Returns event content.

#### `tags()`
Returns all tags as `List<List<String>>`.

#### `sig()`
Returns signature (hex-encoded).

#### `getTagValue(String tagName)`
Returns first value of a tag, or null.

```java
String relay = note.getTagValue("r");
```

#### `getTagValues(String tagName)`
Returns all values of a tag.

```java
List<String> mentions = note.getTagValues("p");
```

#### `toJson()`
Serializes to JSON string.

### Static Methods

#### `fromJson(String json)`
Parses a note from JSON.

```java
Note note = Note.fromJson(jsonString);
```

---

## Profile

User profile data object. Immutable.

### Methods

#### `name()`
Returns username.

#### `displayName()`
Returns display name.

#### `bestDisplayName()`
Returns display_name if set, otherwise name.

#### `about()`
Returns bio/description.

#### `picture()`
Returns avatar URL.

#### `banner()`
Returns banner URL.

#### `nip05()`
Returns NIP-05 identifier.

#### `lud16()`
Returns Lightning address (LUD-16).

#### `lud06()`
Returns LNURL (LUD-06).

#### `bestLightningAddress()`
Returns lud16 if set, otherwise lud06.

#### `website()`
Returns website URL.

### Static Methods

#### `fromJson(String json)`
Parses a profile from JSON.

---

## QueryResult

Query result containing a note key.

### Methods

#### `noteKey()`
Returns the internal note key.

---

## Subscription

Real-time event subscription. Implements `Closeable`.

### Methods

#### `poll(int maxNotes)`
Polls for new note keys.

```java
List<Long> keys = sub.poll(100);
```

#### `poll()`
Polls with default limit of 100.

#### `isActive()`
Checks if subscription is still active.

#### `close()`
Cancels the subscription.

---

## HexUtil

Hex encoding utilities.

### Static Methods

#### `encode(byte[] bytes)`
Encodes bytes to hex string.

```java
String hex = HexUtil.encode(bytes);
```

#### `decode(String hex)`
Decodes hex string to bytes.

```java
byte[] bytes = HexUtil.decode(hex);
```

#### `isValidHex(String hex)`
Validates a hex string.

```java
boolean valid = HexUtil.isValidHex(hex);
```

---

## NostrdbException

Runtime exception for nostrdb errors.

```java
public class NostrdbException extends RuntimeException
```
