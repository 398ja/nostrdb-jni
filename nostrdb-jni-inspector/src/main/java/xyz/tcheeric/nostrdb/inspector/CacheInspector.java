package xyz.tcheeric.nostrdb.inspector;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinJte;
import xyz.tcheeric.nostrdb.Ndb;
import xyz.tcheeric.nostrdb.inspector.api.*;
import xyz.tcheeric.nostrdb.inspector.handlers.*;
import xyz.tcheeric.nostrdb.inspector.middleware.*;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Main entry point for the cache inspector HTTP service.
 * <p>
 * Can be used standalone or embedded within an existing application.
 *
 * <h2>Embedded Usage</h2>
 * <pre>{@code
 * CacheInspector inspector = CacheInspector.builder()
 *     .ndb(ndb)
 *     .port(7777)
 *     .build();
 * inspector.start();
 * // ... later ...
 * inspector.stop();
 * }</pre>
 */
public class CacheInspector {

    private final InspectorConfig config;
    private final AtomicReference<Ndb> ndbRef;
    private final ReadWriteLock rwLock;
    private Javalin app;

    private CacheInspector(InspectorConfig config, Ndb ndb) {
        this.config = config;
        this.ndbRef = new AtomicReference<>(ndb);
        this.rwLock = new ReentrantReadWriteLock();
    }

    /**
     * Start the HTTP server.
     */
    public void start() {
        // Set up JTE template engine
        TemplateEngine templateEngine;
        try {
            // Try precompiled templates first (production)
            templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
        } catch (Exception e) {
            // Fall back to directory resolver (development)
            Path templateDir = Path.of("src/main/jte");
            if (templateDir.toFile().exists()) {
                DirectoryCodeResolver resolver = new DirectoryCodeResolver(templateDir);
                templateEngine = TemplateEngine.create(resolver, ContentType.Html);
            } else {
                throw new RuntimeException("No JTE templates found. " +
                        "Either precompile templates or run from project root.", e);
            }
        }

        JavalinJte.init(templateEngine);

        app = Javalin.create(javalinConfig -> {
            javalinConfig.staticFiles.add("/static");

            // CORS
            if (config.hasCorsOrigin()) {
                javalinConfig.bundledPlugins.enableCors(cors -> {
                    cors.addRule(rule -> {
                        rule.allowHost(config.corsOrigin());
                    });
                });
            }
        });

        // Middleware
        if (config.hasApiKey()) {
            app.before(new AuthMiddleware(config.apiKey()));
        }
        app.before("/api/*", new RateLimitMiddleware(100));

        // Create handlers
        DashboardHandler dashboardHandler = new DashboardHandler(ndbRef, rwLock);
        NotesHandler notesHandler = new NotesHandler(ndbRef, rwLock);
        ProfilesHandler profilesHandler = new ProfilesHandler(ndbRef, rwLock);
        IngestHandler ingestHandler = new IngestHandler(ndbRef, rwLock, config.readOnly());
        ManagementHandler managementHandler = new ManagementHandler(ndbRef, rwLock, config.readOnly());
        FragmentHandler fragmentHandler = new FragmentHandler(ndbRef, rwLock);

        // API handlers
        StatsApiHandler statsApiHandler = new StatsApiHandler(ndbRef, rwLock);
        NotesApiHandler notesApiHandler = new NotesApiHandler(ndbRef, rwLock);
        ProfilesApiHandler profilesApiHandler = new ProfilesApiHandler(ndbRef, rwLock);
        SearchApiHandler searchApiHandler = new SearchApiHandler(ndbRef, rwLock);

        // Rate limiting for write endpoints
        if (!config.readOnly()) {
            app.before("/api/flush", RateLimitMiddleware.forFlush());
            app.before("/api/ingest", RateLimitMiddleware.forIngest());
        }

        // --- Page routes ---
        app.get("/", dashboardHandler);
        app.get("/notes", notesHandler.list());
        app.get("/notes/{id}", notesHandler.detail());
        app.get("/profiles", profilesHandler.list());
        app.get("/profiles/{pubkey}", profilesHandler.detail());
        app.get("/ingest", ingestHandler.page());
        app.get("/management", managementHandler.page());

        // --- API routes ---
        app.get("/api/stats", statsApiHandler);
        app.get("/api/notes", notesApiHandler.list());
        app.get("/api/notes/{id}", notesApiHandler.getById());
        app.get("/api/profiles", profilesApiHandler.search());
        app.get("/api/profiles/{pubkey}", profilesApiHandler.getByPubkey());
        app.get("/api/search", searchApiHandler);
        app.post("/api/flush", managementHandler.flush());
        app.post("/api/ingest", ingestHandler.ingest());

        // --- Fragment routes (HTMX) ---
        app.get("/fragments/stats-panel", fragmentHandler.statsPanel());
        app.get("/fragments/kind-table", fragmentHandler.kindTable());
        app.get("/fragments/notes-table", fragmentHandler.notesTable());
        app.get("/fragments/note-row-detail", fragmentHandler.noteRowDetail());
        app.get("/fragments/profiles-grid", fragmentHandler.profilesGrid());

        app.start(config.host(), config.port());
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    /**
     * Get the current Ndb instance.
     */
    public Ndb getNdb() {
        return ndbRef.get();
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CacheInspector.
     */
    public static class Builder {
        private Ndb ndb;
        private String dbPath;
        private int port = 7777;
        private String host = "127.0.0.1";
        private String apiKey;
        private boolean readOnly = false;
        private String corsOrigin;

        public Builder ndb(Ndb ndb) {
            this.ndb = ndb;
            this.dbPath = ndb.getDbPath();
            return this;
        }

        public Builder dbPath(String dbPath) {
            this.dbPath = dbPath;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder corsOrigin(String corsOrigin) {
            this.corsOrigin = corsOrigin;
            return this;
        }

        public CacheInspector build() {
            if (ndb == null && dbPath != null) {
                ndb = Ndb.open(dbPath);
            }
            if (ndb == null) {
                throw new IllegalStateException("Either ndb or dbPath must be set");
            }
            if (dbPath == null) {
                dbPath = ndb.getDbPath();
            }

            InspectorConfig config = new InspectorConfig(dbPath, port, host, apiKey, readOnly, corsOrigin);
            return new CacheInspector(config, ndb);
        }
    }
}
