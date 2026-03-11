# Cache Inspector API Reference

REST API reference for the nostrdb cache inspector. All endpoints return JSON unless noted otherwise.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/stats` | Database statistics |
| GET | `/api/notes` | Query notes with filtering |
| GET | `/api/notes/{id}` | Get a single note |
| GET | `/api/profiles` | Search profiles |
| GET | `/api/profiles/{pubkey}` | Get a single profile |
| GET | `/api/search` | Full-text search |
| POST | `/api/ingest` | Ingest events |
| POST | `/api/flush` | Flush database |

---

## GET /api/stats

Returns database statistics.

**Response:**

```json
{
  "dbPath": "/path/to/.nostrdb",
  "fileSize": 104857600,
  "fileSizeHuman": "100.0 MB",
  "subscriptionCount": 3,
  "totalEntries": 45230,
  "totalDataSize": 89400320,
  "totalDataSizeHuman": "85.3 MB",
  "dbs": [
    { "index": 0, "keySize": 1234, "valueSize": 56789, "count": 500 }
  ],
  "commonKinds": [
    { "kind": 0, "label": "Metadata", "keySize": 100, "valueSize": 5000, "count": 120 }
  ],
  "otherKinds": { "keySize": 300, "valueSize": 15000, "count": 1200 }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `dbPath` | string | Database directory path |
| `fileSize` | long | LMDB file size in bytes |
| `fileSizeHuman` | string | Human-readable file size |
| `subscriptionCount` | int | Active subscription count |
| `totalEntries` | long | Total entries across all sub-databases |
| `totalDataSize` | long | Total data size in bytes |
| `dbs` | array | Statistics for each of the 16 LMDB sub-databases |
| `commonKinds` | array | Statistics for the 15 most common event kinds |
| `otherKinds` | object | Aggregate statistics for all other kinds |

---

## GET /api/notes

Query notes with filtering and pagination.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `kind` | int (repeatable) | (none) | Filter by event kind(s) |
| `author` | string (repeatable) | (none) | Filter by author pubkey (64-char hex) |
| `since` | long | (none) | Events created after this Unix timestamp |
| `until` | long | (none) | Events created before this Unix timestamp |
| `tag` | string (repeatable) | (none) | Tag filter as `name:value` (e.g., `d:identifier`) |
| `search` | string | (none) | Full-text content search |
| `limit` | int | 50 | Results per page (max 1000) |
| `offset` | int | 0 | Pagination offset |

**Response:**

```json
{
  "total": 150,
  "limit": 50,
  "offset": 0,
  "notes": [
    {
      "id": "d7dd5eb3ab747e16...",
      "pubkey": "32e1827635450ebb...",
      "created_at": 1700000000,
      "kind": 1,
      "content": "Hello, Nostr!",
      "tags": [["p", "..."], ["e", "..."]],
      "sig": "908a15e46fb4d867..."
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `total` | int | Total matching results (before pagination) |
| `limit` | int | Results per page |
| `offset` | int | Current offset |
| `notes` | array | Array of note objects |

---

## GET /api/notes/{id}

Get a single note by its event ID.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | 64-character hex event ID |

**Response (200):** Single note object (same shape as notes array element above).

**Response (400):**
```json
{ "error": "Invalid event ID format" }
```

**Response (404):**
```json
{ "error": "Note not found" }
```

---

## GET /api/profiles

Search profiles by name.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `q` | string | (required) | Name search query |
| `limit` | int | 20 | Max results (max 100) |

**Response:**

```json
{
  "total": 5,
  "profiles": [
    {
      "pubkey": "32e1827635450ebb...",
      "name": "alice",
      "display_name": "Alice",
      "about": "Nostr enthusiast",
      "picture": "https://...",
      "banner": "https://...",
      "nip05": "alice@example.com",
      "lud16": "alice@getalby.com",
      "website": "https://alice.dev"
    }
  ]
}
```

---

## GET /api/profiles/{pubkey}

Get a single profile by public key.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `pubkey` | string | 64-character hex public key |

**Response (200):** Single profile object.

**Response (400):**
```json
{ "error": "Invalid pubkey format" }
```

**Response (404):**
```json
{ "error": "Profile not found" }
```

---

## GET /api/search

Full-text search across note content.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `q` | string | (required) | Search query |
| `kind` | int | (none) | Optional kind filter |
| `limit` | int | 50 | Max results (max 1000) |
| `offset` | int | 0 | Pagination offset |

**Response:** Same format as `GET /api/notes`.

---

## POST /api/ingest

Ingest events into the database. Disabled in read-only mode.

**Request (JSON array):**

```json
{
  "events": [
    {"id":"...","pubkey":"...","created_at":1700000000,"kind":1,"content":"...","tags":[],"sig":"..."}
  ]
}
```

**Request (line-delimited, Content-Type: text/plain):**

```
["EVENT","sub1",{...}]
["EVENT","sub1",{...}]
```

**Response:**

```json
{
  "processed": 42,
  "total": 50,
  "errors": 8
}
```

| Field | Type | Description |
|-------|------|-------------|
| `processed` | int | Events successfully ingested |
| `total` | int | Total events submitted |
| `errors` | int | Events that failed |

**Response (403):** Returned when inspector is in read-only mode.

---

## POST /api/flush

Flush (wipe) the entire database. Disabled in read-only mode.

**Request:**

```json
{
  "confirm": true
}
```

The `confirm` field must be `true` or the request is rejected.

**Response (200):**

```json
{
  "status": "ok",
  "message": "Database flushed and reopened",
  "previousFileSize": 104857600,
  "previousEntries": 45230
}
```

**Response (400):** Missing or false `confirm` field.

**Response (403):** Inspector is in read-only mode.

**Response (409):** Active subscriptions exist (flush could corrupt them).

---

## Error Responses

All errors return JSON with an `error` field:

```json
{ "error": "Description of the error" }
```

| Status | Meaning |
|--------|---------|
| 400 | Bad request (invalid ID format, missing parameters) |
| 401 | Unauthorized (missing or invalid API key) |
| 403 | Forbidden (read-only mode, operation not allowed) |
| 404 | Not found (note or profile doesn't exist) |
| 409 | Conflict (cannot flush while subscriptions are active) |
| 429 | Too many requests (rate limit exceeded) |
| 500 | Internal server error |

## Authentication

When the inspector is started with `--api-key`, all requests must include:

```
X-API-Key: your-secret-key
```

Static asset requests (`/static/*`) are exempt from authentication.

## Rate Limits

| Endpoint | Limit |
|----------|-------|
| `GET /api/*` | 100 req/s per IP |
| `POST /api/ingest` | 10 req/s per IP |
| `POST /api/flush` | 1 req/min per IP |

Exceeding the limit returns `429 Too Many Requests`.
