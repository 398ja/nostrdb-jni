# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-02-14

### Added

- `NdbStat` class for database statistics (record counts, key/value sizes, page usage)
- `Ndb.stat()` method to retrieve database statistics via `ndb_stat`
- `Ndb.getSubscriptionCount()` method to get active subscription count
- `Ndb.getDbFileSize()` method to get database file size in bytes
- Native JNI bindings for `ndb_stat`, subscription count, and DB file size

## [0.1.3] - 2026-02-07

### Changed

- Performance tuning based on audit findings
- Comprehensive security documentation added to public classes

### Fixed

- Security audit findings from Oracle secure coding guidelines
- Limit validation to prevent integer overflow in native code

## [0.1.2] - 2026-01-23

### Added

- `Filter.MAX_LIMIT` constant (100,000,000) to define safe upper bound for query limits
- Limit validation in `Filter.Builder.limit()`, `Ndb.query()`, `Ndb.queryNotes()`, `Ndb.searchProfiles()`, and `Ndb.pollForNotes()`

### Fixed

- Add panic safety to all JNI native functions to prevent JVM crashes
- Rust panics now throw `RuntimeException` with helpful diagnostic messages instead of aborting the JVM
- Protected close/destroy operations and filter builder functions with `catch_unwind`
- Prevent integer overflow by validating limit parameters before passing to native code

## [0.1.1] - 2025-01-22

### Added

- CI workflows for automated builds

### Changed

- Use remote nostrdb git source for native build

## [0.1.0] - 2025-01-21

### Added

- Initial release
- JNI bindings for nostrdb high-performance embedded Nostr database
- Support for event ingestion, querying, and filtering
- Transaction management
- Profile operations
- Subscription support

[Unreleased]: https://github.com/398ja/nostrdb-jni/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/398ja/nostrdb-jni/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/398ja/nostrdb-jni/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/398ja/nostrdb-jni/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/398ja/nostrdb-jni/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/398ja/nostrdb-jni/releases/tag/v0.1.0
