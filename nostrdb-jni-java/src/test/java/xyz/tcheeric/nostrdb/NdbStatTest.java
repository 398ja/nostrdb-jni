package xyz.tcheeric.nostrdb;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the nostrdb stats API: getStats(), getSubscriptionCount(),
 * getDbFileSize(), and isOpen().
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NdbStatTest {

    @TempDir
    static Path tempDir;

    static Ndb ndb;

    static final String TEST_EVENT_JSON = """
        {
            "id": "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027",
            "pubkey": "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245",
            "created_at": 1700000000,
            "kind": 1,
            "tags": [],
            "content": "Test note for stats",
            "sig": "908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262"
        }
        """;

    @BeforeAll
    static void setUp() {
        ndb = Ndb.open(tempDir.resolve("statdb"));
        // Ingest an event (may or may not persist depending on signature validation)
        try {
            ndb.processEvent(TEST_EVENT_JSON);
        } catch (NostrdbException e) {
            // Signature may be invalid - that's OK for stats tests
        }
    }

    @AfterAll
    static void tearDown() {
        if (ndb != null) {
            ndb.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("getStats() should return non-null stats with 16 dbs and 15 common kinds")
    void testGetStats() {
        NdbStat stats = ndb.getStats();

        assertNotNull(stats, "Stats should not be null");
        assertNotNull(stats.dbs(), "dbs list should not be null");
        assertEquals(16, stats.dbs().size(), "Should have 16 LMDB databases");
        assertNotNull(stats.commonKinds(), "commonKinds list should not be null");
        assertEquals(15, stats.commonKinds().size(), "Should have 15 common kind categories");
        assertNotNull(stats.otherKinds(), "otherKinds should not be null");
    }

    @Test
    @Order(2)
    @DisplayName("getStats() totalEventCount and totalDataSize should be non-negative")
    void testGetStatsAggregates() {
        NdbStat stats = ndb.getStats();

        assertTrue(stats.totalEventCount() >= 0, "Total event count should be non-negative");
        assertTrue(stats.totalDataSize() >= 0, "Total data size should be non-negative");
    }

    @Test
    @Order(3)
    @DisplayName("getSubscriptionCount() should return 0 initially")
    void testSubscriptionCountInitial() {
        int count = ndb.getSubscriptionCount();
        assertEquals(0, count, "Should have 0 subscriptions initially");
    }

    @Test
    @Order(4)
    @DisplayName("getSubscriptionCount() should increment after subscribing")
    void testSubscriptionCountAfterSubscribe() {
        // Create a subscription
        try (Filter filter = Filter.builder().kinds(1).limit(10).build()) {
            Subscription sub = ndb.subscribe(filter);
            int count = ndb.getSubscriptionCount();
            assertTrue(count >= 1, "Should have at least 1 subscription after subscribing");
        }
    }

    @Test
    @Order(5)
    @DisplayName("getDbFileSize() should return a positive value for an open database")
    void testGetDbFileSize() {
        long size = ndb.getDbFileSize();
        assertTrue(size > 0, "Database file size should be positive, got: " + size);
    }

    @Test
    @Order(6)
    @DisplayName("isOpen() should return true for an open database")
    void testIsOpenTrue() {
        assertTrue(ndb.isOpen(), "Database should be open");
    }

    @Test
    @Order(7)
    @DisplayName("isOpen() should return false after close")
    void testIsOpenFalseAfterClose() {
        Ndb tempNdb = Ndb.open(tempDir.resolve("closedb"));
        assertTrue(tempNdb.isOpen(), "Should be open initially");
        tempNdb.close();
        assertFalse(tempNdb.isOpen(), "Should be closed after close()");
    }

    @Test
    @Order(8)
    @DisplayName("getStats() should throw when database is closed")
    void testGetStatsThrowsWhenClosed() {
        Ndb closedNdb = Ndb.open(tempDir.resolve("closeddb"));
        closedNdb.close();
        assertThrows(IllegalStateException.class, closedNdb::getStats);
    }

    @Test
    @Order(9)
    @DisplayName("getDbPath() should return the path used to open the database")
    void testGetDbPath() {
        String expected = tempDir.resolve("statdb").toString();
        assertEquals(expected, ndb.getDbPath(), "Should return the path used to open the database");
    }
}
