# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/398ja/nostrdb-jni/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/398ja/nostrdb-jni/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/398ja/nostrdb-jni/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/398ja/nostrdb-jni/releases/tag/v0.1.0
