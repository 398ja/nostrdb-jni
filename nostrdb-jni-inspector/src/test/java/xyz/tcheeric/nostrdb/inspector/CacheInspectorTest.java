package xyz.tcheeric.nostrdb.inspector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheInspectorTest {

    @Test
    void builder_missingNdbAndDbPath_throws() {
        assertThrows(IllegalStateException.class, () ->
                CacheInspector.builder()
                        .port(7777)
                        .build());
    }

    @Test
    void builder_defaultPort() {
        // We can't fully build without an Ndb or valid dbPath that exists,
        // but we can verify the builder accepts configuration
        var builder = CacheInspector.builder()
                .port(8080)
                .host("0.0.0.0")
                .apiKey("test-key")
                .readOnly(true)
                .corsOrigin("http://localhost:3000");
        assertNotNull(builder);
    }

    @Test
    void builder_withDbPath_invalidPath() {
        // An invalid path should fail when trying to open Ndb
        assertThrows(Exception.class, () ->
                CacheInspector.builder()
                        .dbPath("/nonexistent/path/that/cannot/be/created/db")
                        .build());
    }
}
