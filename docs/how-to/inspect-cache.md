# Inspect Cache

The cache inspector is an HTTP sidecar that lets you browse, query, and manage a nostrdb database remotely. It provides a REST API and a server-rendered web UI.

## Standalone Mode

Run the inspector as a standalone JAR:

```bash
java -jar nostrdb-jni-inspector-0.3.0.jar \
  --db-path /path/to/.nostrdb \
  --port 7777 \
  --host 127.0.0.1
```

### CLI Options

| Flag | Default | Description |
|------|---------|-------------|
| `--db-path` | (required) | Path to nostrdb data directory |
| `--port` | `7777` | HTTP listen port |
| `--host` | `127.0.0.1` | Bind address |
| `--api-key` | (none) | Require API key for all requests |
| `--read-only` | `false` | Disable flush and ingest endpoints |
| `--cors-origin` | (none) | Allowed CORS origin |

Open `http://localhost:7777` in a browser to access the web UI.

## Embedded Mode

Share an existing `Ndb` instance with the inspector:

```java
Ndb ndb = Ndb.open("/path/to/.nostrdb");

CacheInspector inspector = CacheInspector.builder()
    .ndb(ndb)
    .port(7777)
    .host("127.0.0.1")
    .readOnly(false)
    .build();

inspector.start();

// ... your application runs ...

inspector.stop();
ndb.close();
```

## Maven Dependency

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostrdb-jni-inspector</artifactId>
    <version>0.3.0</version>
</dependency>
```

## Using the Web UI

The web UI has five sections accessible from the top navigation bar:

### Dashboard

Shows database health at a glance: file size, total entries, data size, and active subscriptions. Includes a per-kind breakdown table. The stats panel auto-refreshes every 5 seconds.

### Notes

Browse and filter notes with the filter form:
- **Kind** - event kind number (e.g., 1 for text notes)
- **Author** - hex pubkey
- **Since/Until** - time range
- **Tags** - tag filters as `name:value` (e.g., `p:pubkeyhex`)
- **Search** - full-text content search

Click a note row to expand its full details inline. Use prev/next for pagination.

### Profiles

Search profiles by name. Click a profile card to see full details and recent notes by that author.

### Ingest

Paste JSON events or upload a `.json`/`.jsonl` file to ingest events into the database. Accepts relay format `["EVENT", "subid", {...}]` or raw event objects.

### Management

Flush (wipe) the entire database. Type "FLUSH" in the confirmation field to enable the button. This closes the database, deletes the LMDB files, and reopens a fresh empty database.

## Using the REST API

All API endpoints return JSON.

### Get Stats

```bash
curl http://localhost:7777/api/stats
```

### Query Notes

```bash
# All notes (default limit 50)
curl http://localhost:7777/api/notes

# Filter by kind and author
curl "http://localhost:7777/api/notes?kind=1&author=32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245&limit=20"

# Full-text search
curl "http://localhost:7777/api/notes?search=hello&kind=1"

# Pagination
curl "http://localhost:7777/api/notes?limit=50&offset=50"

# Tag filter
curl "http://localhost:7777/api/notes?tag=d:my-identifier"
```

### Get Note by ID

```bash
curl http://localhost:7777/api/notes/d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027
```

### Search Profiles

```bash
curl "http://localhost:7777/api/profiles?q=alice&limit=10"
```

### Get Profile by Pubkey

```bash
curl http://localhost:7777/api/profiles/32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245
```

### Full-Text Search

```bash
curl "http://localhost:7777/api/search?q=bitcoin&kind=1&limit=20"
```

### Ingest Events

```bash
curl -X POST http://localhost:7777/api/ingest \
  -H "Content-Type: application/json" \
  -d '{"events": [{"id":"...","pubkey":"...","created_at":1700000000,"kind":1,"content":"hello","tags":[],"sig":"..."}]}'
```

### Flush Database

```bash
curl -X POST http://localhost:7777/api/flush \
  -H "Content-Type: application/json" \
  -d '{"confirm": true}'
```

## Authentication

When `--api-key` is set, all requests (except static assets) must include the key:

```bash
curl -H "X-API-Key: your-secret-key" http://localhost:7777/api/stats
```

The web UI has a key input field that stores the key in `sessionStorage`.

## Read-Only Mode

When `--read-only` is set:
- `POST /api/flush` returns `403 Forbidden`
- `POST /api/ingest` returns `403 Forbidden`
- The web UI hides the Ingest and Management sections

## Rate Limiting

Built-in per-IP rate limiting:
- API endpoints: 100 requests/second
- Flush endpoint: 1 request/minute
- Ingest endpoint: 10 requests/second

## Security Notes

- The inspector binds to `127.0.0.1` by default (localhost only)
- Use `--host 0.0.0.0` to expose to the network, but always pair with `--api-key`
- In production, put the inspector behind a reverse proxy (nginx, Caddy) for TLS
- Use `--read-only` for monitoring-only deployments
