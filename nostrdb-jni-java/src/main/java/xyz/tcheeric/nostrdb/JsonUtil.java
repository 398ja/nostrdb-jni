package xyz.tcheeric.nostrdb;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared JSON utilities for nostrdb.
 *
 * <p>This class provides a shared, thread-safe {@link ObjectMapper} instance
 * for JSON serialization and deserialization. Jackson's ObjectMapper is
 * thread-safe for read operations (serialization/deserialization), so a
 * single shared instance is more efficient than creating multiple instances.
 *
 * <h2>Thread Safety</h2>
 * <p>The {@link #MAPPER} instance is thread-safe for all read operations.
 * Do not modify the ObjectMapper configuration after class initialization.
 */
final class JsonUtil {

    /**
     * Shared ObjectMapper instance for JSON processing.
     *
     * <p>This instance is configured with default settings and is safe for
     * concurrent use across multiple threads.
     */
    static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {}
}
