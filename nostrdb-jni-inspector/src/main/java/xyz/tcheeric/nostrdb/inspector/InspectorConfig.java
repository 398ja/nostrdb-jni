package xyz.tcheeric.nostrdb.inspector;

/**
 * Configuration for the cache inspector.
 *
 * @param dbPath     Path to the nostrdb data directory
 * @param port       HTTP listen port
 * @param host       Bind address
 * @param apiKey     Optional API key for authentication (null = no auth)
 * @param readOnly   If true, disable flush and ingest endpoints
 * @param corsOrigin Allowed CORS origin (null = no CORS headers)
 */
public record InspectorConfig(
        String dbPath,
        int port,
        String host,
        String apiKey,
        boolean readOnly,
        String corsOrigin
) {
    public InspectorConfig {
        if (dbPath == null || dbPath.isBlank()) {
            throw new IllegalArgumentException("dbPath is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasCorsOrigin() {
        return corsOrigin != null && !corsOrigin.isBlank();
    }
}
