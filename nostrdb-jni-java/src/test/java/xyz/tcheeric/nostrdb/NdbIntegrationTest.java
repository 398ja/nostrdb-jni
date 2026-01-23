package xyz.tcheeric.nostrdb;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for nostrdb JNI bindings.
 *
 * <p>Note: nostrdb validates event signatures, so tests with fake events
 * may not persist. These tests focus on API correctness and error handling.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NdbIntegrationTest {

    @TempDir
    static Path tempDir;

    static Ndb ndb;

    // Test event - a valid kind 1 text note (signatures are validated by nostrdb)
    static final String TEST_EVENT_JSON = """
        {
            "id": "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027",
            "pubkey": "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245",
            "created_at": 1700000000,
            "kind": 1,
            "tags": [
                ["p", "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"],
                ["e", "5c83da77af1dec6d7289834998ad7aafbd9e2191396d75ec3cc27f5a77226f36"]
            ],
            "content": "Hello, Nostr! This is a test note from nostrdb-jni.",
            "sig": "908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262"
        }
        """;

    @BeforeAll
    static void setUp() {
        // Verify native library is loaded
        assertTrue(NostrdbNative.isLoaded(), "Native library should be loaded");

        // Open database
        ndb = Ndb.open(tempDir.resolve("testdb"));
        assertNotNull(ndb, "Ndb should not be null");
    }

    @AfterAll
    static void tearDown() {
        if (ndb != null) {
            ndb.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should open database successfully")
    void testOpenDatabase() {
        assertNotNull(ndb);
    }

    @Test
    @Order(2)
    @DisplayName("Should process event without throwing (even if signature invalid)")
    void testProcessEvent() {
        // This may not actually store the event if signature is invalid,
        // but should not throw an exception
        assertDoesNotThrow(() -> {
            try {
                ndb.processEvent(TEST_EVENT_JSON);
            } catch (NostrdbException e) {
                // Expected if signature is invalid
            }
        });
    }

    @Test
    @Order(3)
    @DisplayName("Should begin and end transaction")
    void testTransaction() {
        try (Transaction txn = ndb.beginTransaction()) {
            assertNotNull(txn, "Transaction should not be null");
            assertTrue(txn.isOpen(), "Transaction should be open");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Transaction should be closed after close()")
    void testTransactionClosed() {
        Transaction txn = ndb.beginTransaction();
        assertTrue(txn.isOpen());
        txn.close();
        assertFalse(txn.isOpen());
        assertThrows(IllegalStateException.class, txn::ptr);
    }

    @Test
    @Order(5)
    @DisplayName("Should return empty for non-existent note")
    void testNonExistentNote() {
        try (Transaction txn = ndb.beginTransaction()) {
            String fakeId = "0000000000000000000000000000000000000000000000000000000000000000";
            Optional<Note> note = ndb.getNoteById(txn, fakeId);
            assertTrue(note.isEmpty(), "Should not find non-existent note");
        }
    }

    @Test
    @Order(6)
    @DisplayName("Should build filter with kinds")
    void testFilterBuilder() {
        try (Filter filter = Filter.builder()
                .kinds(1, 3, 7)
                .limit(100)
                .build()) {
            assertNotNull(filter);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Should build filter with authors")
    void testFilterWithAuthors() {
        try (Filter filter = Filter.builder()
                .authors("32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245")
                .limit(10)
                .build()) {
            assertNotNull(filter);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Should build filter with time range")
    void testFilterWithTimeRange() {
        try (Filter filter = Filter.builder()
                .since(1699999999L)
                .until(1700000002L)
                .limit(10)
                .build()) {
            assertNotNull(filter);
        }
    }

    @Test
    @Order(9)
    @DisplayName("Should build filter with tags")
    void testFilterWithTags() {
        try (Filter filter = Filter.builder()
                .kinds(1)
                .pTag("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d")
                .dTag("test-identifier")
                .limit(10)
                .build()) {
            assertNotNull(filter);
        }
    }

    @Test
    @Order(10)
    @DisplayName("Should execute query without errors")
    void testQueryExecution() {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(1)
                 .limit(10)
                 .build()) {

            List<QueryResult> results = ndb.query(txn, filter);
            assertNotNull(results);
            // Results may be empty if no valid events were stored
        }
    }

    @Test
    @Order(11)
    @DisplayName("Should execute queryNotes without errors")
    void testQueryNotes() {
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder()
                 .kinds(1)
                 .limit(10)
                 .build()) {

            List<Note> notes = ndb.queryNotes(txn, filter, 10);
            assertNotNull(notes);
        }
    }

    @Test
    @Order(12)
    @DisplayName("Should create and close subscription")
    void testSubscription() {
        try (Filter filter = Filter.builder().kinds(1).build();
             Subscription sub = ndb.subscribe(filter)) {

            assertTrue(sub.isActive(), "Subscription should be active");

            // Poll for notes (may be empty)
            List<Long> noteKeys = sub.poll(10);
            assertNotNull(noteKeys);
        }
        // After close, the subscription should be inactive
    }

    @Test
    @Order(13)
    @DisplayName("Should throw on invalid event ID length")
    void testInvalidEventIdLength() {
        try (Transaction txn = ndb.beginTransaction()) {
            assertThrows(IllegalArgumentException.class, () ->
                ndb.getNoteById(txn, new byte[16]) // Should be 32 bytes
            );
        }
    }

    @Test
    @Order(14)
    @DisplayName("Should throw on invalid pubkey length")
    void testInvalidPubkeyLength() {
        try (Transaction txn = ndb.beginTransaction()) {
            assertThrows(IllegalArgumentException.class, () ->
                ndb.getProfileByPubkey(txn, new byte[16]) // Should be 32 bytes
            );
        }
    }

    @Test
    @Order(15)
    @DisplayName("Should parse Note from JSON")
    void testNoteFromJson() {
        Note note = Note.fromJson(TEST_EVENT_JSON);

        assertEquals("d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027", note.id());
        assertEquals("32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245", note.pubkey());
        assertEquals(1700000000L, note.createdAt());
        assertEquals(1, note.kind());
        assertEquals("Hello, Nostr! This is a test note from nostrdb-jni.", note.content());
        assertNotNull(note.tags());
        assertEquals(2, note.tags().size());

        // Test tag methods
        String pTagValue = note.getTagValue("p");
        assertEquals("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d", pTagValue);

        List<String> eTags = note.getTagValues("e");
        assertEquals(1, eTags.size());
        assertEquals("5c83da77af1dec6d7289834998ad7aafbd9e2191396d75ec3cc27f5a77226f36", eTags.get(0));
    }

    @Test
    @Order(16)
    @DisplayName("Should parse Profile from JSON")
    void testProfileFromJson() {
        String profileJson = """
            {
                "name": "jb55",
                "display_name": "Will",
                "about": "nostrdb creator",
                "picture": "https://example.com/avatar.jpg",
                "nip05": "jb55@jb55.com",
                "lud16": "jb55@getalby.com"
            }
            """;

        Profile profile = Profile.fromJson(profileJson);

        assertEquals("jb55", profile.name());
        assertEquals("Will", profile.displayName());
        assertEquals("nostrdb creator", profile.about());
        assertEquals("https://example.com/avatar.jpg", profile.picture());
        assertEquals("jb55@jb55.com", profile.nip05());
        assertEquals("jb55@getalby.com", profile.lud16());
        assertEquals("Will", profile.bestDisplayName());
        assertEquals("jb55@getalby.com", profile.bestLightningAddress());
    }

    @Test
    @Order(17)
    @DisplayName("Should handle HexUtil encode/decode")
    void testHexUtil() {
        byte[] original = new byte[] {0x00, 0x01, 0x0f, (byte) 0xff, (byte) 0xab};
        String hex = HexUtil.encode(original);
        assertEquals("00010fffab", hex);

        byte[] decoded = HexUtil.decode(hex);
        assertArrayEquals(original, decoded);

        assertTrue(HexUtil.isValidHex("0123456789abcdef"));
        assertTrue(HexUtil.isValidHex("ABCDEF")); // uppercase
        assertFalse(HexUtil.isValidHex("xyz"));
        assertFalse(HexUtil.isValidHex("123")); // Odd length
        assertFalse(HexUtil.isValidHex(null));
    }

    @Test
    @Order(18)
    @DisplayName("Should handle filter builder errors")
    void testFilterBuilderErrors() {
        Filter.Builder builder = Filter.builder();
        Filter filter = builder.kinds(1).build();

        // Builder should throw after build
        assertThrows(IllegalStateException.class, () -> builder.kinds(2));

        filter.close();
    }

    @Test
    @Order(19)
    @DisplayName("Should handle closed filter")
    void testClosedFilter() {
        Filter filter = Filter.builder().kinds(1).build();
        filter.close();

        assertThrows(IllegalStateException.class, filter::ptr);
    }

    @Test
    @Order(20)
    @DisplayName("Should search profiles without errors")
    void testSearchProfiles() {
        try (Transaction txn = ndb.beginTransaction()) {
            List<byte[]> results = ndb.searchProfiles(txn, "test", 10);
            assertNotNull(results);
            // Results may be empty
        }
    }

    @Test
    @Order(21)
    @DisplayName("Note toString should be safe")
    void testNoteToString() {
        Note note = Note.fromJson(TEST_EVENT_JSON);
        String str = note.toString();
        assertNotNull(str);
        assertTrue(str.contains("Note{"));
    }

    @Test
    @Order(22)
    @DisplayName("QueryResult should have equality")
    void testQueryResultEquality() {
        // Use reflection-style test via parseResults
        byte[] data = new byte[12]; // 4 bytes count + 8 bytes key
        data[0] = 1; // count = 1
        data[4] = 42; // key = 42

        List<QueryResult> results = QueryResult.parseResults(data);
        assertEquals(1, results.size());
        assertEquals(42, results.get(0).noteKey());

        // Test equality
        QueryResult r1 = results.get(0);
        QueryResult r2 = results.get(0);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    // Limit validation tests - prevent integer overflow in native code

    @Test
    @Order(23)
    @DisplayName("Filter.Builder.limit() should reject zero limit")
    void testFilterBuilderRejectsZeroLimit() {
        // Test that zero limit is rejected
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Filter.builder().kinds(1).limit(0).build()
        );
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    @Order(24)
    @DisplayName("Filter.Builder.limit() should reject negative limit")
    void testFilterBuilderRejectsNegativeLimit() {
        // Test that negative limit is rejected
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Filter.builder().kinds(1).limit(-1).build()
        );
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    @Order(25)
    @DisplayName("Filter.Builder.limit() should reject excessive limit")
    void testFilterBuilderRejectsExcessiveLimit() {
        // Test that limit exceeding MAX_LIMIT is rejected
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Filter.builder().kinds(1).limit(Filter.MAX_LIMIT + 1).build()
        );
        assertTrue(ex.getMessage().contains("maximum"));
    }

    @Test
    @Order(26)
    @DisplayName("Ndb.query() should reject excessive limit")
    void testNdbQueryRejectsExcessiveLimit() {
        // Test that query with excessive limit is rejected
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder().kinds(1).build()) {

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    ndb.query(txn, filter, Filter.MAX_LIMIT + 1)
            );
            assertTrue(ex.getMessage().contains("maximum"));
        }
    }

    @Test
    @Order(27)
    @DisplayName("Ndb.queryNotes() should reject excessive limit")
    void testNdbQueryNotesRejectsExcessiveLimit() {
        // Test that queryNotes with excessive limit is rejected
        try (Transaction txn = ndb.beginTransaction();
             Filter filter = Filter.builder().kinds(1).build()) {

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    ndb.queryNotes(txn, filter, Integer.MAX_VALUE)
            );
            assertTrue(ex.getMessage().contains("maximum"));
        }
    }

    @Test
    @Order(28)
    @DisplayName("Ndb.searchProfiles() should reject excessive limit")
    void testNdbSearchProfilesRejectsExcessiveLimit() {
        // Test that searchProfiles with excessive limit is rejected
        try (Transaction txn = ndb.beginTransaction()) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    ndb.searchProfiles(txn, "test", Integer.MAX_VALUE)
            );
            assertTrue(ex.getMessage().contains("maximum"));
        }
    }

    @Test
    @Order(29)
    @DisplayName("Filter.MAX_LIMIT should prevent integer overflow")
    void testMaxLimitPreventsOverflow() {
        // Verify that MAX_LIMIT * 10 does not overflow
        // This is the operation that was causing the native panic
        long result = (long) Filter.MAX_LIMIT * 10;
        assertTrue(result <= Integer.MAX_VALUE,
                "MAX_LIMIT * 10 should not exceed Integer.MAX_VALUE");
        assertTrue(result > 0,
                "MAX_LIMIT * 10 should be positive (no overflow)");
    }
}
