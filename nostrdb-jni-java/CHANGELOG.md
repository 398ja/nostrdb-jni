# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.1] - 2026-03-06

### Fixed

- Handle packed ID tag values in native `serialize_note` — tags like `["p","<pubkey>"]` were truncated to `["p"]` due to `NDB_PACKED_ID` binary values being silently dropped

## [0.1.3] - 2026-02-02

### Added

- `JsonUtil` class with shared `ObjectMapper` instance for improved performance
- Comprehensive security documentation in Javadocs for all public classes
- Thread safety documentation for `Ndb`, `Transaction`, `Filter`, `Subscription`
- Input validation limits: `MAX_KINDS`, `MAX_AUTHORS`, `MAX_TAG_VALUES`, `MAX_SEARCH_LENGTH`, `MAX_TAG_VALUE_LENGTH`

### Changed

- `Note.tags()` now returns an unmodifiable view of nested lists
- `Ndb.searchProfiles()` and `Ndb.pollForNotes()` now return unmodifiable lists
- `HexUtil` is now package-private (internal use only)
- `NostrdbException` is now `final` to prevent subclassing
- `NativeLoader` refactored to use try-with-resources pattern with proper error cleanup
- `NostrdbNative` now uses holder class pattern for lock-free lazy initialization

### Fixed

- Added null/blank validation for JSON input in `processEvent()` and `processEvents()`
- Added defensive copies for byte[] inputs to prevent TOCTOU attacks
- Added path validation for database path in `Ndb.open()`
- Improved temp file cleanup in `NativeLoader` on error

### Security

- Input validation on all public wrapper methods before passing to native code
- Resource exhaustion protection through enforced size limits on filter parameters
- Defensive copies prevent callers from modifying parameters during method execution

## [0.1.2] - 2024-XX-XX

### Fixed

- Add limit validation to prevent integer overflow in native code
- Add panic safety to JNI functions to prevent JVM crashes

## [0.1.1] - 2024-XX-XX

- Initial documented release

## [0.1.0] - 2024-XX-XX

- Initial release

[0.2.1]: https://github.com/398ja/nostrdb-jni/compare/v0.2.0...v0.2.1
[0.1.3]: https://github.com/398ja/nostrdb-jni/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/398ja/nostrdb-jni/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/398ja/nostrdb-jni/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/398ja/nostrdb-jni/releases/tag/v0.1.0
