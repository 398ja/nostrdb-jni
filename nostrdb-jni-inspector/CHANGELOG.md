# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-03-11

### Added

- Initial release of cache inspector module
- REST API endpoints: stats, notes, profiles, search, flush, ingest
- Server-rendered Web UI with JTE templates and HTMX
- Dashboard with database statistics, kind breakdowns, and auto-refresh
- Note browser with filtering by kind, author, time range, tags, and full-text search
- Profile browser with name search
- Event ingestion via JSON or line-delimited JSON
- Database flush with confirmation safeguard
- API key authentication middleware
- Rate limiting middleware
- Standalone mode (fat JAR with CLI args via picocli)
- Embedded mode (share existing Ndb instance)
- Docker Compose setup for deployment

[0.3.0]: https://github.com/398ja/nostrdb-jni/releases/tag/v0.3.0
