# Audit Report

**Source:** [Oracle Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html)
**Date:** 2026-02-02
**Codebase:** nostrdb-jni (xyz.tcheeric:nostrdb-jni v0.1.2)

## Executive Summary

- **Total Guidelines Evaluated:** 54
- **Applicable to Codebase:** 22
- **Findings:** 11 (0 critical, 4 high, 5 medium, 2 low)
- **Compliance Score:** 50% (11/22 applicable guidelines have findings)

### Implementation Status

**Fixed in commit `5c8a541`:**
- [INPUT-3] ✅ COMPLIANT - Added null/blank validation for JSON input
- [MUTABLE-3] ✅ COMPLIANT - Added defensive copies for byte[] inputs
- [MUTABLE-12] ✅ COMPLIANT - Returns unmodifiable collections
- [DOS-1] ✅ COMPLIANT - Added MAX_KINDS, MAX_AUTHORS, MAX_TAG_VALUES limits
- [DOS-2] ✅ COMPLIANT - Improved NativeLoader with try-with-resources
- [EXTEND-1] ✅ COMPLIANT - Made HexUtil package-private
- [EXTEND-5] ✅ COMPLIANT - Made NostrdbException final
- [INPUT-1] ✅ COMPLIANT - Added MAX_SEARCH_LENGTH, MAX_TAG_VALUE_LENGTH validation

## Codebase Capabilities Detected

| Capability | Status | Key Files |
|------------|--------|-----------|
| Native Methods (JNI) | Present | `NostrdbNative.java` |
| File I/O | Present | `NativeLoader.java`, `Ndb.java` |
| Resource Management | Present | `Ndb.java`, `Transaction.java`, `Filter.java`, `Subscription.java` |
| JSON Parsing | Present | `Note.java`, `Profile.java` |
| Cryptography | Not Present | - |
| Database (SQL) | Not Present | - |
| Serialization | Not Present | - |
| HTTP/Network | Not Present | - |
| XML Processing | Not Present | - |
| Reflection | Not Present | - |
| Process Execution | Not Present | - |
| Authentication | Not Present | - |
| Logging | Not Present | - |

## Findings

### High Severity

---

#### [INPUT-3] Create wrapper methods around native code

**Status:** ✅ COMPLIANT (Fixed in commit `5c8a541`)
**Guideline:** Declare native methods private and expose through public Java wrappers. Wrappers perform input validation before delegating to native code, providing bounds checking and type safety.
**Source:** [Section 5: Input Validation](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:**
The codebase correctly declares all native methods as package-private in `NostrdbNative.java` (good). The `Ndb.java` class provides public wrapper methods that perform some validation. However, several native method wrappers lack complete input validation.

**Locations:**
- `NostrdbNative.java:68` - `ndbOpen()` receives path directly
- `NostrdbNative.java:88` - `processEvent()` receives JSON directly
- `NostrdbNative.java:131` - `getNoteById()` receives eventId array
- `NostrdbNative.java:260` - `getProfileByPubkey()` receives pubkey array

**Current Code (Good Pattern - already implemented for some):**
```java
// Ndb.java:137-144 - Good validation present
public Optional<Note> getNoteById(Transaction txn, byte[] eventId) {
    checkOpen();
    if (eventId == null || eventId.length != 32) {
        throw new IllegalArgumentException("Event ID must be 32 bytes");
    }
    byte[] data = NostrdbNative.getNoteById(ptr, txn.ptr(), eventId);
    return Optional.ofNullable(data).map(Note::fromBytes);
}
```

**Missing Validation:**
```java
// Ndb.java:89-95 - No JSON validation before passing to native
public void processEvent(String json) {
    checkOpen();
    int result = NostrdbNative.processEvent(ptr, json);  // No null check on json
    if (result == 0) {
        throw new NostrdbException("Failed to process event");
    }
}
```

**Recommended Fix:**
```java
public void processEvent(String json) {
    checkOpen();
    if (json == null || json.isBlank()) {
        throw new IllegalArgumentException("JSON event cannot be null or blank");
    }
    int result = NostrdbNative.processEvent(ptr, json);
    if (result == 0) {
        throw new NostrdbException("Failed to process event");
    }
}
```

---

#### [MUTABLE-3] Create defensive copies of untrusted mutable input

**Status:** ✅ COMPLIANT (Fixed in commit `5c8a541`)
**Guideline:** Copy mutable and subclassable input parameters before storing or using them. Defend against TOCTOU (time-of-check, time-of-use) race conditions where callers modify parameters during method execution.
**Source:** [Section 6: Mutability](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations:**
- `Ndb.java:137-144` - `getNoteById()` passes byte[] directly to native code
- `Ndb.java:227-234` - `getProfileByPubkey()` passes byte[] directly to native code
- `Filter.java:140-159` - `authors(byte[]...)` passes arrays without cloning

**Current Code:**
```java
// Ndb.java:137-144
public Optional<Note> getNoteById(Transaction txn, byte[] eventId) {
    checkOpen();
    if (eventId == null || eventId.length != 32) {
        throw new IllegalArgumentException("Event ID must be 32 bytes");
    }
    byte[] data = NostrdbNative.getNoteById(ptr, txn.ptr(), eventId);  // eventId not cloned
    return Optional.ofNullable(data).map(Note::fromBytes);
}
```

**Recommended Fix:**
```java
public Optional<Note> getNoteById(Transaction txn, byte[] eventId) {
    checkOpen();
    if (eventId == null || eventId.length != 32) {
        throw new IllegalArgumentException("Event ID must be 32 bytes");
    }
    eventId = eventId.clone();  // Defensive copy
    byte[] data = NostrdbNative.getNoteById(ptr, txn.ptr(), eventId);
    return Optional.ofNullable(data).map(Note::fromBytes);
}
```

---

#### [MUTABLE-12] Don't expose modifiable collections

**Status:** ✅ COMPLIANT (Fixed in commit `5c8a541`)
**Guideline:** Use `List.of()`, `Collections.unmodifiable*()`, or `copyOf()` to expose read-only collections. For collections containing mutable elements, expose deep copies.
**Source:** [Section 6: Mutability](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations:**
- `Note.java:132-134` - `tags()` returns mutable reference
- `Ndb.java:256-276` - `searchProfiles()` returns `List<byte[]>` with mutable byte arrays

**Current Code:**
```java
// Note.java:132-134
public List<List<String>> tags() {
    return tags;  // Returns internal mutable reference
}
```

**Recommended Fix:**
```java
public List<List<String>> tags() {
    if (tags == null) return List.of();
    // Return unmodifiable view with unmodifiable inner lists
    return tags.stream()
        .map(List::copyOf)
        .collect(Collectors.toUnmodifiableList());
}
```

**For searchProfiles:**
```java
// The returned byte[] arrays are mutable. Each should be cloned:
public List<byte[]> searchProfiles(Transaction txn, String query, int limit) {
    // ... existing code ...
    List<byte[]> pubkeys = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
        byte[] pubkey = new byte[32];
        buf.get(pubkey);
        pubkeys.add(pubkey);  // Already creating new arrays, but list should be unmodifiable
    }
    return Collections.unmodifiableList(pubkeys);
}
```

---

#### [MUTABLE-9] Always declare public static fields final

**Status:** COMPLIANT
**Guideline:** Public non-final static fields are trivially modifiable without validation. Declare public static fields `final` always.

**Locations:**
- `Filter.java:32` - `public static final int MAX_LIMIT = 100_000_000;`

**Analysis:** The codebase correctly uses `public static final` for the only public static field. However, there are private static fields that should be reviewed.

**Private Static Fields (NEEDS REVIEW):**
- `Profile.java:29` - `private static final ObjectMapper MAPPER` - Good, immutable
- `Note.java:29` - `private static final ObjectMapper MAPPER` - Good, immutable
- `NostrdbNative.java:15-16` - `loaded` and `loadError` are volatile but not final

---

### Medium Severity

---

#### [DOS-1] Guard against resource exhaustion attacks

**Status:** ✅ COMPLIANT (Fixed in commit `5c8a541`)
**Guideline:** Validate that input will not cause disproportionate resource consumption (CPU, memory, disk, file descriptors). Check for integer overflow in size calculations.
**Source:** [Section 1: Denial of Service](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:**
The codebase has recently added limit validation (good - see `Filter.MAX_LIMIT`), but some areas remain unvalidated.

**Locations:**
- `Ndb.java:103-110` - `processEvents()` accepts arbitrary-length LDJSON without size limits
- `Filter.java:91-108` - `kinds()` allocates buffer based on array length without limit

**Current Code:**
```java
// Filter.java:91-108
public Builder kinds(int... kinds) {
    checkNotBuilt();
    if (kinds == null || kinds.length == 0) {
        return this;
    }
    // No limit on kinds.length - could allocate very large buffer
    ByteBuffer buf = ByteBuffer.allocate(kinds.length * 4)
        .order(ByteOrder.LITTLE_ENDIAN);
    // ...
}
```

**Recommended Fix:**
```java
private static final int MAX_KINDS = 1000;  // Reasonable limit

public Builder kinds(int... kinds) {
    checkNotBuilt();
    if (kinds == null || kinds.length == 0) {
        return this;
    }
    if (kinds.length > MAX_KINDS) {
        throw new IllegalArgumentException("Too many kinds: " + kinds.length + " (max: " + MAX_KINDS + ")");
    }
    ByteBuffer buf = ByteBuffer.allocate(kinds.length * 4)
        .order(ByteOrder.LITTLE_ENDIAN);
    // ...
}
```

---

#### [DOS-2] Reliably release resources regardless of execution path

**Status:** ✅ COMPLIANT (Fixed in commit `5c8a541`)
**Guideline:** Pair acquire and release operations for resources like files, locks, and allocated memory. Use try-with-resources (Java 7+) or Execute Around Method pattern with try-finally blocks.
**Source:** [Section 1: Denial of Service](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:**
The codebase properly implements `Closeable` for resource management (good). However, example code and some internal patterns could leak resources on exceptions.

**Compliant Areas:**
- `Ndb.java` implements `Closeable`
- `Transaction.java` implements `Closeable`
- `Filter.java` implements `Closeable`
- `Subscription.java` implements `Closeable`
- Test code uses try-with-resources correctly

**Potential Issue:**
- `NativeLoader.java:60-72` - If `Files.copy` or `System.load` throws, the temp file is not cleaned up properly (only scheduled for deleteOnExit).

**Current Code:**
```java
// NativeLoader.java:60-72
try {
    Path tempFile = Files.createTempFile("nostrdb-", libFileName);
    tempFile.toFile().deleteOnExit();
    Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
    System.load(tempFile.toAbsolutePath().toString());
} finally {
    is.close();
}
```

**Recommended Fix:**
```java
Path tempFile = null;
try {
    tempFile = Files.createTempFile("nostrdb-", libFileName);
    tempFile.toFile().deleteOnExit();
    Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
    System.load(tempFile.toAbsolutePath().toString());
} catch (Exception e) {
    if (tempFile != null) {
        try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
    }
    throw e;
} finally {
    is.close();
}
```

---

#### [CONFIDENTIAL-1] Remove sensitive information from exception objects

**Status:** PARTIAL
**Guideline:** Catch internal exceptions and sanitize before propagating. Even exception types reveal information. Include only known-safe information in exceptions.
**Source:** [Section 2: Confidential Information](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations:**
- `Ndb.java:75-76` - Exception includes file path
- `NativeLoader.java:56-57` - Exception includes attempted resource paths

**Current Code:**
```java
// Ndb.java:75-76
if (ptr == 0) {
    throw new NostrdbException("Failed to open database at " + dbPath);
}
```

**Analysis:**
For a database library, including the path in error messages is often necessary for debugging. However, callers should be aware that exception messages may contain paths. Consider documenting this behavior or providing a way to suppress path information in exceptions for production deployments.

**NEEDS REVIEW:** This is context-dependent. If this library will be used in environments where file paths are sensitive (e.g., shared hosting), consider adding a configuration option.

---

#### [EXTEND-1] Restrict class, method, and field accessibility

**Status:** ✅ COMPLIANT (Fixed in commit `5c8a541`)
**Guideline:** Declare as public only classes/methods/fields that are published API components. Otherwise, use package-private or private access.
**Source:** [Section 4: Accessibility and Extensibility](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:**
The codebase generally follows good access control patterns, but some classes could be more restrictive.

**Good Patterns:**
- `NostrdbNative.java` - Package-private class with package-private methods
- `NativeLoader.java` - Package-private class
- All native pointer (`ptr`) fields are package-private

**Potential Improvements:**
- `HexUtil.java:6` - Public utility class. Could be package-private if only used internally.
- `NostrdbException.java:6` - Public exception. This is correct for API exceptions.

**Current Code:**
```java
// HexUtil.java
public final class HexUtil { ... }
```

**NEEDS REVIEW:** If `HexUtil` is intended as part of the public API (for users to encode/decode hex), it should remain public. If only used internally, make it package-private.

---

#### [EXTEND-5] Prevent unsafe class extension and method override

**Status:** COMPLIANT
**Guideline:** Declare classes `final` or use sealed classes to control inheritance. Prevent method override through design or final declarations.
**Source:** [Section 4: Accessibility and Extensibility](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:**
All classes are declared `final`, preventing subclassing:
- `Ndb.java:45` - `public final class Ndb`
- `Transaction.java:20` - `public final class Transaction`
- `Filter.java:24` - `public final class Filter`
- `Subscription.java:30` - `public final class Subscription`
- `Note.java:27` - `public final class Note`
- `Profile.java:27` - `public final class Profile`
- `QueryResult.java:14` - `public final class QueryResult`
- `HexUtil.java:6` - `public final class HexUtil`
- `NostrdbNative.java:13` - `final class NostrdbNative`
- `NativeLoader.java:12` - `final class NativeLoader`

**Exception:**
- `NostrdbException.java:6` - ✅ Now `public final class NostrdbException extends RuntimeException` (Fixed in commit `5c8a541`)

---

#### [MUTABLE-1] Favor immutable value types

**Status:** PARTIAL
**Guideline:** Make classes immutable to prevent mutable-object security issues. Hide constructors (private or package-private) and provide builders. Declare fields `final`.
**Source:** [Section 6: Mutability](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:**
Most value types are properly immutable.

**Compliant:**
- `Note.java` - All fields are final, uses `@JsonCreator`
- `Profile.java` - All fields are final, uses `@JsonCreator`
- `QueryResult.java` - Single final field, private constructor

**Partial:**
- `Filter.Builder` - Mutable by design (builder pattern), but properly tracks state with `built` flag

---

### Low Severity

---

#### [FUNDAMENTALS-7] Document security-relevant API information

**Status:** PARTIAL
**Guideline:** Include in documentation: required permissions, security-related exceptions, caller sensitivity, preconditions, postconditions, and checked exception specifications.
**Source:** [Section 0: Fundamentals](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:**
The codebase has good Javadoc documentation overall, but some security-relevant aspects are not documented.

**Missing Documentation:**
- Thread-safety constraints (only partially documented)
- Maximum input sizes/limits
- Native library loading behavior and security implications

**Example Good Documentation Already Present:**
```java
/**
 * Begin a read transaction.
 *
 * <p><b>IMPORTANT:</b> Only one transaction per thread is allowed (LMDB constraint).
 * Always use try-with-resources to ensure proper cleanup.
 */
```

**Recommended Additions:**
- Document that `Filter.MAX_LIMIT` exists and why
- Document that native library is loaded from JAR or `java.library.path`
- Document thread-safety for all public methods

---

#### [INPUT-1] Validate all external inputs

**Status:** ✅ COMPLIANT (Fixed in commit `5c8a541`)
**Guideline:** Check input from untrusted sources, method arguments, and external streams before use. Validate at multiple points: early to reject sooner, and immediately before security-sensitive operations.
**Source:** [Section 5: Input Validation](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:**
Some validation is present, but inconsistent across methods.

**Good Validation:**
- `Ndb.java:139-141` - eventId length check
- `Ndb.java:229-231` - pubkey length check
- `Filter.java:248-256` - limit validation with bounds checking
- `HexUtil.java:40-55` - hex string validation

**Previously Missing Validation (now fixed):**
- ✅ `Filter.Builder.search(String search)` - Now has MAX_SEARCH_LENGTH check
- ✅ `Filter.Builder.tag(String tagName, String... values)` - Now has MAX_TAG_VALUES and MAX_TAG_VALUE_LENGTH checks
- `Ndb.open(String dbPath)` - No path validation (low priority, pending)

---

## Compliant Areas

The codebase demonstrates good adherence to several security guidelines:

1. **EXTEND-5 (Final Classes):** All main classes are declared `final`, preventing inheritance attacks. `NostrdbException` is now also final.

2. **DOS-2 (Resource Management):** Proper use of `Closeable` interface and try-with-resources in examples/tests. `NativeLoader` now uses try-with-resources with proper cleanup on error.

3. **OBJECT-1 (Construction Control):** Uses static factory methods (`Ndb.open()`, `Filter.builder()`) rather than public constructors for main API classes.

4. **DOS-3 (Integer Overflow Prevention):** `Filter.MAX_LIMIT` prevents integer overflow. Now also includes `MAX_KINDS`, `MAX_AUTHORS`, `MAX_TAG_VALUES`, `MAX_SEARCH_LENGTH`, and `MAX_TAG_VALUE_LENGTH`.

5. **MUTABLE-9 (Static Finals):** Public static fields are declared final.

6. **FUNDAMENTALS-6 (Encapsulation):** Native pointers (`ptr`) are properly encapsulated as package-private fields. `HexUtil` is now package-private.

7. **MUTABLE-3 (Defensive Copies):** All byte[] inputs to native methods are now cloned before use.

8. **MUTABLE-12 (Unmodifiable Collections):** `tags()`, `searchProfiles()`, and `pollForNotes()` now return unmodifiable collections.

9. **INPUT-3 (Input Validation):** All public wrapper methods now validate inputs before passing to native code.

## Implementation Plan

Ordered list of changes to achieve full compliance, prioritized by severity and effort.

### Phase 1: Critical Fixes (Immediate)

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| - | No critical findings | - | - | - |

### Phase 2: High Priority

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 1 | Add null/blank check for JSON input in `processEvent()` and `processEvents()` | `Ndb.java` | Low | ✅ Done |
| 2 | Clone byte array inputs before passing to native methods | `Ndb.java`, `Filter.java` | Low | ✅ Done |
| 3 | Return unmodifiable collections from `tags()` and `searchProfiles()` | `Note.java`, `Ndb.java` | Low | ✅ Done |

### Phase 3: Medium Priority

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 4 | Add size limits to filter builder methods (`kinds()`, `authors()`, `tag()`) | `Filter.java` | Low | ✅ Done |
| 5 | Improve temp file cleanup in NativeLoader on error | `NativeLoader.java` | Low | ✅ Done |
| 6 | Review and reduce access modifiers where appropriate (`HexUtil`) | `HexUtil.java` | Low | ✅ Done |
| 7 | Make `NostrdbException` final | `NostrdbException.java` | Low | ✅ Done |

### Phase 4: Low Priority / Nice-to-Have

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 8 | Add comprehensive security documentation to Javadoc | All public classes | Med | ✅ Done |
| 9 | Add max length validation for search queries and tag values | `Filter.java` | Low | ✅ Done |
| 10 | Add path validation for database path | `Ndb.java` | Low | ✅ Done |

## Guidelines Not Applicable

The following guidelines were evaluated but are not applicable to this codebase:

| Guideline | Reason |
|-----------|--------|
| INJECT-2 (SQL Injection) | No SQL/database access |
| INJECT-3 (XSS) | No HTML/web output generation |
| INJECT-4 (Command Injection) | No process/command execution |
| INJECT-5 (XXE) | No XML processing |
| SERIAL-1 through SERIAL-6 | No Java serialization used |
| DOS-5 (Hash Collision) | No user-controlled HashMap keys in security-sensitive context |
| CONFIDENTIAL-2 (Logging) | No logging framework used |
| CONFIDENTIAL-3 (Memory Zeroing) | No sensitive cryptographic data handled |

---
*Generated by `/audit` skill on 2026-02-02*
*Source: [Oracle Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html)*

*Fixes implemented on 2026-02-02 in commits `5c8a541` and `87d65ad` on branch `fix/security-audit-findings`*
*All 10 implementation tasks completed.*