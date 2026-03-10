# nostrdb Cache Inspector — Specification

**Status:** Draft
**Date:** 2026-03-10
**Module:** `nostrdb-jni-inspector`

---

## 1. Overview

A lightweight HTTP service that exposes the nostrdb LMDB cache for remote inspection and management. Runs as a sidecar alongside any application using `nostrdb-jni`, providing a REST API and a server-rendered web UI built with JTE templates and HTMX.

### Problem

The nostrdb cache typically runs on a remote server. There is no way to browse, query, or manage its contents without SSH access and custom code.

### Goals

- Remote inspection of all cached data (notes, profiles, statistics)
- Flexible querying with the full power of nostrdb filters
- Database management (flush, re-ingest)
- Minimal footprint — single JAR, no frontend build tooling (JTE + HTMX, all server-rendered)
- Secure by default — bind to localhost, optional API key auth

### Non-Goals

- Individual event deletion (not supported by nostrdb at any level — the database is append-only)
- Relay protocol implementation (resync will accept pre-fetched JSON, not connect to relays directly)
- Multi-database management (one inspector instance per nostrdb instance)

---

## 2. Architecture

```
┌──────────────────────────────────────────┐
│            nostrdb-jni-inspector         │
│                                          │
│  ┌──────────────────────────────────────┐  │
│  │         Javalin HTTP Server         │  │
│  │                                     │  │
│  │  ┌─────────────┐ ┌──────────────┐  │  │
│  │  │ JTE         │ │ JSON API     │  │  │
│  │  │ Templates   │ │              │  │  │
│  │  │ (HTML views)│ │ /api/stats   │  │  │
│  │  │      +      │ │ /api/notes   │  │  │
│  │  │ HTMX        │ │ /api/profiles│  │  │
│  │  │ (fragments) │ │ /api/search  │  │  │
│  │  │             │ │ /api/flush   │  │  │
│  │  │             │ │ /api/ingest  │  │  │
│  │  └─────────────┘ └──────────────┘  │  │
│  └──────────────────┬─────────────────┘  │
│                          │               │
│                 ┌────────▼────────────┐  │
│                 │  nostrdb-jni-java   │  │
│                 │  (Ndb, Filter, etc) │  │
│                 └────────┬────────────┘  │
│                          │               │
│                 ┌────────▼────────────┐  │
│                 │  LMDB (data.mdb)    │  │
│                 └─────────────────────┘  │
└──────────────────────────────────────────┘
```

### Deployment Modes

1. **Standalone JAR** — `java -jar nostrdb-inspector.jar --db-path /path/to/.nostrdb`
2. **Embedded** — Instantiate `CacheInspector` programmatically within an existing app, sharing the same `Ndb` instance
3. **Docker sidecar** — Mount the LMDB data directory as a volume

---

## 3. REST API

All responses are JSON. All endpoints return appropriate HTTP status codes.

### 3.1 Database Statistics

```
GET /api/stats
```

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
    { "index": 0, "keySize": 1234, "valueSize": 56789, "count": 500 },
    ...
  ],
  "commonKinds": [
    { "kind": 0, "label": "Metadata", "keySize": 100, "valueSize": 5000, "count": 120 },
    { "kind": 1, "label": "Short Text Note", "keySize": 200, "valueSize": 30000, "count": 8500 },
    { "kind": 3, "label": "Contacts", "keySize": 50, "valueSize": 8000, "count": 80 },
    { "kind": 7, "label": "Reaction", "keySize": 150, "valueSize": 2000, "count": 3200 },
    ...
  ],
  "otherKinds": { "keySize": 300, "valueSize": 15000, "count": 1200 }
}
```

### 3.2 Query Notes

```
GET /api/notes?kind=1&author=<hex>&since=<unix>&until=<unix>&tag=<name>:<value>&limit=50&offset=0
```

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `kind` | int (repeatable) | Filter by event kind(s) |
| `author` | hex string (repeatable) | Filter by author pubkey(s) |
| `since` | unix timestamp | Events created after this time |
| `until` | unix timestamp | Events created before this time |
| `tag` | `name:value` (repeatable) | Filter by tag (e.g., `d:identifier`, `p:<pubkey>`, `e:<eventid>`) |
| `search` | string | Full-text search |
| `limit` | int (default: 50, max: 1000) | Results per page |
| `offset` | int (default: 0) | Pagination offset (implemented via over-fetch + skip) |

**Response:**
```json
{
  "total": 150,
  "limit": 50,
  "offset": 0,
  "notes": [
    {
      "id": "aabbccdd...",
      "pubkey": "11223344...",
      "created_at": 1709942400,
      "kind": 1,
      "content": "Hello world",
      "tags": [["p", "..."], ["e", "..."]],
      "sig": "eeff0011..."
    },
    ...
  ]
}
```

### 3.3 Get Single Note

```
GET /api/notes/:id
```

**Path Parameters:**
- `id` — 64-character hex event ID

**Response:** Single note object (same shape as in array above), or `404`.

### 3.4 Search Profiles

```
GET /api/profiles?q=<query>&limit=20
```

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Name search query |
| `limit` | int (default: 20, max: 100) | Max results |

**Response:**
```json
{
  "total": 5,
  "profiles": [
    {
      "pubkey": "11223344...",
      "name": "alice",
      "display_name": "Alice",
      "about": "Nostr enthusiast",
      "picture": "https://...",
      "banner": "https://...",
      "nip05": "alice@example.com",
      "lud16": "alice@getalby.com",
      "website": "https://alice.dev"
    },
    ...
  ]
}
```

### 3.5 Get Single Profile

```
GET /api/profiles/:pubkey
```

**Path Parameters:**
- `pubkey` — 64-character hex public key

**Response:** Single profile object, or `404`.

### 3.6 Full-Text Search

```
GET /api/search?q=<text>&kind=1&limit=50
```

Combines `Filter.search()` with optional kind filtering. Response format matches `/api/notes`.

### 3.7 Flush Database

```
POST /api/flush
Content-Type: application/json

{
  "confirm": true
}
```

**Behavior:**
1. Closes the current `Ndb` instance
2. Deletes `data.mdb` and `lock.mdb` from the database directory
3. Reopens `Ndb` at the same path (creates fresh empty database)

**Response:**
```json
{
  "status": "ok",
  "message": "Database flushed and reopened",
  "previousFileSize": 104857600,
  "previousEntries": 45230
}
```

**Safeguards:**
- Requires `"confirm": true` in request body
- If API key auth is enabled, requires valid key
- Returns `409 Conflict` if there are active subscriptions

### 3.8 Ingest Events

```
POST /api/ingest
Content-Type: application/json

{
  "events": [
    {"id":"...","pubkey":"...","created_at":...,"kind":1,"content":"...","tags":[],"sig":"..."},
    ...
  ]
}
```

Or line-delimited JSON in the body with `Content-Type: text/plain`:

```
POST /api/ingest
Content-Type: text/plain

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

---

## 4. Web UI

Server-rendered HTML using JTE (Java Template Engine) templates with HTMX for interactivity. No JavaScript framework, no build tooling, no client-side state management.

### Technology Stack

- **JTE** — Type-safe Java templates, compiled at build time, first-class Javalin integration
- **HTMX** — HTML attributes for AJAX requests, DOM swapping, and server-sent events (single `<script>` tag, ~14 KB gzipped)
- **CSS** — Embedded stylesheet, no framework (or optionally PicoCSS ~10 KB for classless styling)

### How It Works

1. Full page loads hit Javalin routes that render JTE templates (e.g., `GET /` → `dashboard.jte`)
2. Interactive elements use HTMX attributes to request **HTML fragments** from the server
3. The server returns partial HTML (rendered from JTE fragment templates) that HTMX swaps into the DOM
4. No JSON parsing in the browser — the server does all formatting and rendering

Example flow — searching notes:
```
User types in search box
  → HTMX sends GET /fragments/notes-table?search=hello (triggered on keyup, 300ms debounce)
  → Server queries nostrdb, renders notes-table.jte fragment with results
  → HTMX swaps the <tbody> with the server response
```

### Templates

#### Layout

```
templates/
├── layout/
│   └── main.jte              # Base layout: nav, header, footer, HTMX script tag
├── pages/
│   ├── dashboard.jte          # Full dashboard page
│   ├── notes.jte              # Full notes browser page
│   ├── note-detail.jte        # Single note detail page
│   ├── profiles.jte           # Full profiles browser page
│   ├── profile-detail.jte     # Single profile detail page
│   ├── ingest.jte             # Ingest page
│   └── management.jte         # Flush / management page
└── fragments/
    ├── stats-panel.jte        # Stats cards (auto-refreshed via hx-trigger="every 5s")
    ├── kind-table.jte         # Kind breakdown table rows
    ├── notes-table.jte        # Notes result rows (for search/filter/pagination)
    ├── note-row-detail.jte    # Expanded note detail (inline in table)
    ├── profiles-grid.jte      # Profile cards grid
    ├── profile-card.jte       # Single profile card
    ├── ingest-result.jte      # Ingest operation result summary
    └── flush-result.jte       # Flush operation result
```

#### Template Data Models

JTE templates receive typed Java records as parameters — no untyped maps or string keys:

```java
// Dashboard data
record DashboardModel(
    String dbPath,
    String fileSize,          // pre-formatted: "100.0 MB"
    int subscriptionCount,
    long totalEntries,
    String totalDataSize,     // pre-formatted
    List<KindStat> kindStats,
    List<DbStat> dbStats
) {}

record KindStat(int kind, String label, long count, String dataSize) {}
record DbStat(int index, long count, String keySize, String valueSize) {}

// Notes page data
record NotesPageModel(
    List<NoteViewModel> notes,
    NotesFilter activeFilter,  // to re-populate form fields
    int total,
    int limit,
    int offset,
    boolean hasNext,
    boolean hasPrev
) {}

record NoteViewModel(
    String id,                // full hex
    String idShort,           // first 8 + "..." + last 8
    String pubkey,
    String pubkeyShort,
    String authorName,        // resolved from profile, or null
    int kind,
    String kindLabel,
    String content,
    String contentTruncated,  // first 200 chars
    String createdAt,         // formatted: "2026-03-10 14:30:00"
    String createdAtRelative, // "2 hours ago"
    List<List<String>> tags,
    String sig
) {}

// Profile data
record ProfileViewModel(
    String pubkey,
    String pubkeyShort,
    String name,
    String displayName,
    String bestName,
    String about,
    String aboutTruncated,
    String picture,
    String banner,
    String nip05,
    String lud16,
    String website,
    long noteCount           // number of notes by this author in cache
) {}
```

### Pages / Views

#### 4.1 Dashboard (`GET /`)
- Stats cards at top: file size, total entries, total data size, subscription count
- Per-kind breakdown table with columns: kind, label, count, data size
- Per-sub-database table
- Stats panel auto-refreshes via `hx-trigger="every 5s"` on the stats fragment
- No full page reload needed

#### 4.2 Note Browser (`GET /notes`)
- Filter form with HTMX: submitting the form sends `GET /fragments/notes-table?...` and swaps the results area
- Debounced search input (300ms) for full-text search
- Results table: id (truncated, linked to detail), kind (with label), author (truncated, linked to profile), created_at (formatted), content (truncated)
- Click a row → `hx-get="/fragments/note-row-detail?id=..."` expands inline below the row
- Pagination: prev/next buttons use `hx-get` with offset parameter, swap the table body
- Filter state preserved in URL query params via `hx-push-url="true"`

#### 4.3 Note Detail (`GET /notes/:id`)
- Full page view for a single note
- All fields displayed: id, pubkey (with profile name if available), kind, created_at, content (full), tags (formatted table), signature
- Raw JSON toggle (pre-formatted `<code>` block)
- Link to author's profile
- Back to browser link (preserves previous filter)

#### 4.4 Profile Browser (`GET /profiles`)
- Search input with debounce → `hx-get="/fragments/profiles-grid?q=..."` swaps the results area
- Results as cards in a CSS grid: avatar image, display name, name, nip05, about (truncated)
- Click card → navigates to profile detail page
- "View notes" link on each card → navigates to notes browser filtered by author

#### 4.5 Profile Detail (`GET /profiles/:pubkey`)
- Full profile display: avatar, banner, all metadata fields
- Recent notes by this author (last 20), loaded as a fragment
- Link to notes browser filtered by this author

#### 4.6 Ingest (`GET /ingest`)
- Textarea for pasting JSON events
- File upload input for `.json` / `.jsonl` files
- Submit button → `hx-post="/api/ingest"` with `hx-target="#ingest-result"`, swaps in `ingest-result.jte` fragment
- Result summary shows processed/total/errors count
- Hidden when `--read-only` is set

#### 4.7 Management (`GET /management`)
- Current DB path display (read-only)
- Current DB stats summary
- Flush section: text input that must contain "FLUSH" to enable the button
- Flush button → `hx-post="/api/flush"` with confirmation body, swaps in `flush-result.jte` fragment
- Result shows previous size/entries and success message
- Hidden when `--read-only` is set

### Navigation

Top navigation bar with links: Dashboard | Notes | Profiles | Ingest | Management

Active page is highlighted. Navigation uses standard `<a>` links (full page loads) — HTMX is used for in-page interactivity only, keeping the app bookmarkable and browser-history friendly.

### Styling

- Classless or minimal-class CSS approach
- System font stack
- Responsive: single column on mobile, multi-column on desktop
- Dark/light theme via `prefers-color-scheme` media query
- Monospace font for hex IDs, signatures, and raw JSON
- Subtle loading indicators via HTMX's `htmx-indicator` class (shown during requests)

---

## 5. Configuration

### 5.1 Standalone Mode

```bash
java -jar nostrdb-inspector.jar \
  --db-path /path/to/.nostrdb \
  --port 7777 \
  --host 127.0.0.1 \
  --api-key "optional-secret"
```

| Flag | Default | Description |
|------|---------|-------------|
| `--db-path` | (required) | Path to nostrdb data directory |
| `--port` | `7777` | HTTP listen port |
| `--host` | `127.0.0.1` | Bind address (localhost only by default) |
| `--api-key` | (none) | If set, all requests must include `X-API-Key` header |
| `--read-only` | `false` | Disable flush and ingest endpoints |
| `--cors-origin` | (none) | Allowed CORS origin, if needed |

### 5.2 Embedded Mode (Library)

```java
Ndb ndb = Ndb.open("/path/to/.nostrdb");

CacheInspector inspector = CacheInspector.builder()
    .ndb(ndb)                    // share existing Ndb instance
    .port(7777)
    .host("127.0.0.1")
    .apiKey("optional-secret")
    .readOnly(false)
    .build();

inspector.start();
// ... later ...
inspector.stop();
```

When embedded, flush closes and reopens the shared `Ndb` instance. The host application must handle the brief unavailability window, or use read-only mode.

---

## 6. Security

### 6.1 Defaults
- Binds to `127.0.0.1` only (not exposed to network)
- No CORS headers by default
- Flush/ingest require explicit confirmation

### 6.2 API Key Authentication
When `--api-key` is set:
- All requests must include `X-API-Key: <key>` header
- Missing or wrong key returns `401 Unauthorized`
- The web UI includes a key input field that stores the key in `sessionStorage`

### 6.3 Read-Only Mode
When `--read-only` is set:
- `POST /api/flush` returns `403 Forbidden`
- `POST /api/ingest` returns `403 Forbidden`
- Web UI hides management and ingest sections

### 6.4 Rate Limiting
- Query endpoints: 100 req/s per IP (configurable)
- Flush endpoint: 1 req/min
- Ingest endpoint: 10 req/s

---

## 7. Module Structure

```
nostrdb-jni-inspector/
├── pom.xml
└── src/main/
    ├── java/xyz/tcheeric/nostrdb/inspector/
    │   ├── CacheInspector.java          # Main entry, Javalin setup, lifecycle
    │   ├── InspectorConfig.java         # Configuration record
    │   ├── handlers/
    │   │   ├── DashboardHandler.java    # GET / (renders dashboard.jte)
    │   │   ├── NotesHandler.java        # GET /notes, GET /notes/:id
    │   │   ├── ProfilesHandler.java     # GET /profiles, GET /profiles/:pk
    │   │   ├── IngestHandler.java       # GET /ingest, POST /api/ingest
    │   │   ├── ManagementHandler.java   # GET /management, POST /api/flush
    │   │   └── FragmentHandler.java     # GET /fragments/* (HTMX partial responses)
    │   ├── api/
    │   │   ├── StatsApiHandler.java     # GET /api/stats (JSON)
    │   │   ├── NotesApiHandler.java     # GET /api/notes, /api/notes/:id (JSON)
    │   │   ├── ProfilesApiHandler.java  # GET /api/profiles, /api/profiles/:pk (JSON)
    │   │   └── SearchApiHandler.java    # GET /api/search (JSON)
    │   ├── model/
    │   │   ├── DashboardModel.java      # View model for dashboard
    │   │   ├── NotesPageModel.java      # View model for notes page
    │   │   ├── NoteViewModel.java       # Formatted note for display
    │   │   ├── ProfileViewModel.java    # Formatted profile for display
    │   │   ├── KindStat.java            # Kind statistics record
    │   │   └── NotesFilter.java         # Active filter state (for form re-population)
    │   ├── middleware/
    │   │   ├── AuthMiddleware.java       # API key validation
    │   │   └── RateLimitMiddleware.java  # Rate limiting
    │   ├── util/
    │   │   ├── Formatting.java          # Human-readable sizes, dates, hex truncation
    │   │   └── KindLabels.java          # Kind number → label map
    │   └── Main.java                    # CLI entry point (args parsing)
    └── jte/                             # JTE template source directory
        ├── layout/
        │   └── main.jte                 # Base layout: nav, header, footer, HTMX script
        ├── pages/
        │   ├── dashboard.jte
        │   ├── notes.jte
        │   ├── note-detail.jte
        │   ├── profiles.jte
        │   ├── profile-detail.jte
        │   ├── ingest.jte
        │   └── management.jte
        └── fragments/
            ├── stats-panel.jte          # Auto-refreshing stats cards
            ├── kind-table.jte           # Kind breakdown rows
            ├── notes-table.jte          # Note result rows
            ├── note-row-detail.jte      # Inline expanded note
            ├── profiles-grid.jte        # Profile cards
            ├── ingest-result.jte        # Ingest result summary
            └── flush-result.jte         # Flush result message
```

### Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `nostrdb-jni` | 0.2.1+ | Core database access |
| `io.javalin:javalin` | 6.x | HTTP server (~1 MB) |
| `io.javalin:javalin-rendering` | 6.x | Template engine integration for Javalin |
| `gg.jte:jte` | 3.x | JTE template engine (compile-time type-safe templates) |
| `com.fasterxml.jackson.core:jackson-databind` | 2.17.x | JSON for API endpoints (already a transitive dep) |
| `info.picocli:picocli` | 4.x | CLI argument parsing (standalone mode) |

HTMX is included as a single `<script>` tag in `main.jte` from a vendored copy in `src/main/resources/static/htmx.min.js` (~14 KB gzipped). No NPM, no CDN dependency.

### Build

```xml
<packaging>jar</packaging>

<plugins>
    <!-- JTE precompilation: templates are compiled to Java classes at build time -->
    <plugin>
        <groupId>gg.jte</groupId>
        <artifactId>jte-maven-plugin</artifactId>
        <version>3.x</version>
        <configuration>
            <sourceDirectory>${project.basedir}/src/main/jte</sourceDirectory>
            <contentType>Html</contentType>
        </configuration>
        <executions>
            <execution>
                <phase>generate-sources</phase>
                <goals>
                    <goal>precompile</goal>
                </goals>
            </execution>
        </executions>
    </plugin>

    <!-- Fat JAR for standalone mode -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <configuration>
            <transformers>
                <transformer implementation="...ManifestResourceTransformer">
                    <mainClass>xyz.tcheeric.nostrdb.inspector.Main</mainClass>
                </transformer>
            </transformers>
        </configuration>
    </plugin>
</plugins>
```

---

## 8. Implementation Notes

### 8.1 Pagination
nostrdb filters don't support offset natively. Pagination is implemented by:
- Over-fetching: request `offset + limit` results from nostrdb
- Skipping the first `offset` results in Java
- This is acceptable for reasonable offsets (<10,000). For deep pagination, the UI should encourage narrowing the filter.

### 8.2 Flush Lifecycle
Since nostrdb doesn't support individual deletes, flush is all-or-nothing:
1. Record current stats (for response)
2. Close `Ndb` instance
3. Delete `data.mdb` and `lock.mdb`
4. Reopen `Ndb` at same path
5. In embedded mode, swap the shared reference atomically (`AtomicReference<Ndb>`)

### 8.3 Thread Safety
- Javalin handles requests on a thread pool
- Each request handler opens its own `Transaction` (LMDB requirement: 1 txn per thread)
- The `Ndb` instance is shared safely (it's thread-safe)
- Flush uses a `ReadWriteLock` — flush takes write lock, all other handlers take read lock

### 8.4 Kind Labels
Map known kind numbers to human-readable labels for the UI:

| Kind | Label |
|------|-------|
| 0 | Metadata |
| 1 | Short Text Note |
| 2 | Recommend Relay |
| 3 | Contacts |
| 4 | Encrypted DM |
| 5 | Event Deletion |
| 6 | Repost |
| 7 | Reaction |
| 9735 | Zap |
| 10002 | Relay List |
| 30023 | Long-form Content |
| 30078 | Application-specific Data |
| ... | (extensible map) |

### 8.5 Human-Readable Formatting
- File sizes: bytes → KB/MB/GB
- Timestamps: Unix → ISO 8601 + relative ("2 hours ago")
- Hex IDs: truncated in lists (`aabb...ccdd`), full in detail views
- Pubkeys: show profile name alongside hex where available
- All formatting is done server-side in view model records — templates receive pre-formatted strings

### 8.6 JTE + HTMX Patterns

**Template rendering:** Javalin's `javalinJte` plugin registers JTE as the rendering engine. Page handlers call `ctx.render("pages/notes.jte", model)`. Fragment handlers call `ctx.render("fragments/notes-table.jte", model)`.

**HTMX request detection:** Fragment endpoints check for the `HX-Request` header. If present, they return only the fragment. If absent (direct browser navigation), they return the full page. This makes all views bookmarkable.

**HTMX attributes used:**
- `hx-get` / `hx-post` — AJAX requests
- `hx-target` — where to swap the response
- `hx-swap` — swap strategy (`innerHTML`, `outerHTML`, `afterend`)
- `hx-trigger` — event triggers (`click`, `keyup changed delay:300ms`, `every 5s`)
- `hx-push-url` — update browser URL for bookmarkability
- `hx-indicator` — show loading spinner during requests
- `hx-confirm` — browser confirmation dialog (used for flush)

**No client-side JavaScript** beyond HTMX itself. All logic, formatting, and state live on the server. The templates are pure HTML with HTMX attributes.

**Precompiled templates:** JTE templates are compiled to `.class` files at build time via the Maven plugin. This gives:
- Compile-time type checking (mismatched model fields = build error)
- Zero startup cost (no runtime template parsing)
- Better performance than interpreted template engines

---

## 9. Future Extensions (Out of Scope for v1)

- **Relay sync** — Built-in WebSocket relay client to fetch events from specific relays and ingest them
- **Event diffing** — Compare cache contents against a relay to find missing events
- **Export** — Download filtered results as JSON/JSONL
- **Webhooks** — Notify external systems when new events are ingested
- **Subscription viewer** — Show active subscriptions and their filters
- **Per-kind deletion** — If nostrdb-rs ever exposes delete APIs
- **Multi-database** — Manage multiple nostrdb instances from one inspector
- **Authentication providers** — OAuth, mTLS for production deployments

---

## 10. Open Questions

1. **Shared vs. exclusive Ndb access** — In standalone mode, opening the LMDB database requires exclusive writer access. If another process already has the DB open, the inspector must either:
   - Open in read-only mode (no ingest/flush)
   - Require the other process to be stopped
   - Share the `Ndb` instance via embedded mode

   *Recommendation:* Default to read-only if the DB is already locked; require embedded mode for write operations on a live system.

2. **Flush safety in embedded mode** — Swapping the `Ndb` reference while other threads may hold active transactions. The `ReadWriteLock` approach handles this, but the host application needs to be aware that its own `Ndb` reference becomes invalid after flush.

   *Recommendation:* In embedded mode, provide a callback (`Consumer<Ndb>`) that the host app registers to receive the new `Ndb` instance after flush.

3. **Maximum response size** — Queries can return very large result sets. Should we enforce a hard cap (e.g., 1000 notes per response) regardless of what the user requests?

   *Recommendation:* Yes. Cap at 1000 per page to prevent OOM and slow responses. Use pagination for larger result sets.
