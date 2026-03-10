package xyz.tcheeric.nostrdb.inspector;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CLI entry point for the cache inspector standalone mode.
 *
 * <pre>
 * java -jar nostrdb-inspector.jar --db-path /path/to/.nostrdb --port 7777
 * </pre>
 */
@Command(
        name = "nostrdb-inspector",
        mixinStandardHelpOptions = true,
        version = "nostrdb-inspector 0.1.0",
        description = "Cache inspector for nostrdb — remote database inspection and management"
)
public class Main implements Callable<Integer> {

    @Option(names = {"--db-path"}, required = true, description = "Path to nostrdb data directory")
    private String dbPath;

    @Option(names = {"--port"}, defaultValue = "7777", description = "HTTP listen port (default: 7777)")
    private int port;

    @Option(names = {"--host"}, defaultValue = "127.0.0.1", description = "Bind address (default: 127.0.0.1)")
    private String host;

    @Option(names = {"--api-key"}, description = "API key for authentication")
    private String apiKey;

    @Option(names = {"--read-only"}, defaultValue = "false", description = "Disable flush and ingest endpoints")
    private boolean readOnly;

    @Option(names = {"--cors-origin"}, description = "Allowed CORS origin")
    private String corsOrigin;

    @Override
    public Integer call() {
        CacheInspector inspector = CacheInspector.builder()
                .dbPath(dbPath)
                .port(port)
                .host(host)
                .apiKey(apiKey)
                .readOnly(readOnly)
                .corsOrigin(corsOrigin)
                .build();

        // Shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down inspector...");
            inspector.stop();
            inspector.getNdb().close();
        }));

        inspector.start();
        System.out.println("nostrdb inspector running at http://" + host + ":" + port);
        System.out.println("Database: " + dbPath);
        if (readOnly) {
            System.out.println("Mode: read-only");
        }

        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
