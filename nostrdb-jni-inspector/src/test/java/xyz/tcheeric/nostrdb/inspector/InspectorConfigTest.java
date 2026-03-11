package xyz.tcheeric.nostrdb.inspector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InspectorConfigTest {

    // ========================================================================
    // Construction & Validation
    // ========================================================================

    @Test
    void validConfig() {
        var config = new InspectorConfig("/path/to/db", 7777, "127.0.0.1", null, false, null);
        assertEquals("/path/to/db", config.dbPath());
        assertEquals(7777, config.port());
        assertEquals("127.0.0.1", config.host());
        assertNull(config.apiKey());
        assertFalse(config.readOnly());
        assertNull(config.corsOrigin());
    }

    @Test
    void fullConfig() {
        var config = new InspectorConfig("/db", 8080, "0.0.0.0", "secret-key", true, "http://localhost:3000");
        assertEquals("/db", config.dbPath());
        assertEquals(8080, config.port());
        assertEquals("0.0.0.0", config.host());
        assertEquals("secret-key", config.apiKey());
        assertTrue(config.readOnly());
        assertEquals("http://localhost:3000", config.corsOrigin());
    }

    @Test
    void nullDbPath_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new InspectorConfig(null, 7777, "127.0.0.1", null, false, null));
    }

    @Test
    void blankDbPath_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new InspectorConfig("  ", 7777, "127.0.0.1", null, false, null));
    }

    @Test
    void emptyDbPath_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new InspectorConfig("", 7777, "127.0.0.1", null, false, null));
    }

    @Test
    void portZero_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new InspectorConfig("/db", 0, "127.0.0.1", null, false, null));
    }

    @Test
    void portNegative_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new InspectorConfig("/db", -1, "127.0.0.1", null, false, null));
    }

    @Test
    void portTooHigh_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new InspectorConfig("/db", 65536, "127.0.0.1", null, false, null));
    }

    @Test
    void portBoundary_min() {
        var config = new InspectorConfig("/db", 1, "127.0.0.1", null, false, null);
        assertEquals(1, config.port());
    }

    @Test
    void portBoundary_max() {
        var config = new InspectorConfig("/db", 65535, "127.0.0.1", null, false, null);
        assertEquals(65535, config.port());
    }

    @Test
    void nullHost_defaultsToLocalhost() {
        var config = new InspectorConfig("/db", 7777, null, null, false, null);
        assertEquals("127.0.0.1", config.host());
    }

    @Test
    void blankHost_defaultsToLocalhost() {
        var config = new InspectorConfig("/db", 7777, "  ", null, false, null);
        assertEquals("127.0.0.1", config.host());
    }

    // ========================================================================
    // hasApiKey
    // ========================================================================

    @Test
    void hasApiKey_null() {
        var config = new InspectorConfig("/db", 7777, "127.0.0.1", null, false, null);
        assertFalse(config.hasApiKey());
    }

    @Test
    void hasApiKey_blank() {
        var config = new InspectorConfig("/db", 7777, "127.0.0.1", "  ", false, null);
        assertFalse(config.hasApiKey());
    }

    @Test
    void hasApiKey_set() {
        var config = new InspectorConfig("/db", 7777, "127.0.0.1", "my-key", false, null);
        assertTrue(config.hasApiKey());
    }

    // ========================================================================
    // hasCorsOrigin
    // ========================================================================

    @Test
    void hasCorsOrigin_null() {
        var config = new InspectorConfig("/db", 7777, "127.0.0.1", null, false, null);
        assertFalse(config.hasCorsOrigin());
    }

    @Test
    void hasCorsOrigin_blank() {
        var config = new InspectorConfig("/db", 7777, "127.0.0.1", null, false, "  ");
        assertFalse(config.hasCorsOrigin());
    }

    @Test
    void hasCorsOrigin_set() {
        var config = new InspectorConfig("/db", 7777, "127.0.0.1", null, false, "http://localhost:3000");
        assertTrue(config.hasCorsOrigin());
    }
}
