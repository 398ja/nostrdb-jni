package xyz.tcheeric.nostrdb.inspector.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import xyz.tcheeric.nostrdb.Ndb;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @TempDir
    static Path tempDir;

    static Ndb ndb;
    static AtomicReference<Ndb> ndbRef;
    static ReadWriteLock rwLock;
    static ObjectMapper mapper = new ObjectMapper();

    // A valid signed test event
    static final String TEST_EVENT = """
            ["EVENT", "test", {"id":"d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027","pubkey":"32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245","created_at":1700000000,"kind":1,"tags":[["p","32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"]],"content":"Hello, Nostr! This is a test note from nostrdb-jni.","sig":"908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262"}]""";

    @BeforeAll
    static void setUp() {
        ndb = Ndb.open(tempDir.resolve("testdb"));
        ndbRef = new AtomicReference<>(ndb);
        rwLock = new ReentrantReadWriteLock();

        // Ingest a test event
        try {
            ndb.processEvent(TEST_EVENT);
        } catch (Exception e) {
            // May fail due to signature validation in some environments
        }
    }

    @AfterAll
    static void tearDown() {
        if (ndb != null) {
            ndb.close();
        }
    }

    private Javalin createApp() {
        Javalin app = Javalin.create();
        app.get("/api/stats", new StatsApiHandler(ndbRef, rwLock));
        app.get("/api/notes", new NotesApiHandler(ndbRef, rwLock).list());
        app.get("/api/notes/{id}", new NotesApiHandler(ndbRef, rwLock).getById());
        app.get("/api/profiles", new ProfilesApiHandler(ndbRef, rwLock).search());
        app.get("/api/profiles/{pubkey}", new ProfilesApiHandler(ndbRef, rwLock).getByPubkey());
        app.get("/api/search", new SearchApiHandler(ndbRef, rwLock));
        return app;
    }

    // ========================================================================
    // Stats API
    // ========================================================================

    @Test
    @Order(1)
    void stats_returnsValidJson() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/stats");
            assertEquals(200, response.code());

            JsonNode json = mapper.readTree(response.body().string());
            assertTrue(json.has("dbPath"));
            assertTrue(json.has("fileSize"));
            assertTrue(json.has("fileSizeHuman"));
            assertTrue(json.has("subscriptionCount"));
            assertTrue(json.has("totalEntries"));
            assertTrue(json.has("totalDataSize"));
            assertTrue(json.has("totalDataSizeHuman"));
            assertTrue(json.has("dbs"));
            assertTrue(json.get("dbs").isArray());
        });
    }

    @Test
    @Order(2)
    void stats_fileSizeIsPositive() {
        JavalinTest.test(createApp(), (server, client) -> {
            JsonNode json = mapper.readTree(client.get("/api/stats").body().string());
            assertTrue(json.get("fileSize").asLong() > 0);
        });
    }

    @Test
    @Order(3)
    void stats_has16Dbs() {
        JavalinTest.test(createApp(), (server, client) -> {
            JsonNode json = mapper.readTree(client.get("/api/stats").body().string());
            assertEquals(16, json.get("dbs").size());
        });
    }

    // ========================================================================
    // Notes API
    // ========================================================================

    @Test
    @Order(10)
    void notes_listReturnsValidStructure() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/notes");
            assertEquals(200, response.code());

            JsonNode json = mapper.readTree(response.body().string());
            assertTrue(json.has("total"));
            assertTrue(json.has("limit"));
            assertTrue(json.has("offset"));
            assertTrue(json.has("notes"));
            assertTrue(json.get("notes").isArray());
        });
    }

    @Test
    @Order(11)
    void notes_defaultLimitIs50() {
        JavalinTest.test(createApp(), (server, client) -> {
            JsonNode json = mapper.readTree(client.get("/api/notes").body().string());
            assertEquals(50, json.get("limit").asInt());
            assertEquals(0, json.get("offset").asInt());
        });
    }

    @Test
    @Order(12)
    void notes_customLimit() {
        JavalinTest.test(createApp(), (server, client) -> {
            JsonNode json = mapper.readTree(client.get("/api/notes?limit=10").body().string());
            assertEquals(10, json.get("limit").asInt());
        });
    }

    @Test
    @Order(13)
    void notes_limitCappedAt1000() {
        JavalinTest.test(createApp(), (server, client) -> {
            JsonNode json = mapper.readTree(client.get("/api/notes?limit=5000").body().string());
            assertEquals(1000, json.get("limit").asInt());
        });
    }

    @Test
    @Order(14)
    void notes_filterByKind() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/notes?kind=1");
            assertEquals(200, response.code());

            JsonNode json = mapper.readTree(response.body().string());
            for (JsonNode note : json.get("notes")) {
                assertEquals(1, note.get("kind").asInt());
            }
        });
    }

    @Test
    @Order(15)
    void notes_getById_invalidId() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/notes/not-a-valid-hex-id");
            assertEquals(400, response.code());

            JsonNode json = mapper.readTree(response.body().string());
            assertTrue(json.has("error"));
        });
    }

    @Test
    @Order(16)
    void notes_getById_notFound() {
        JavalinTest.test(createApp(), (server, client) -> {
            // Valid hex format but doesn't exist
            Response response = client.get("/api/notes/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            assertEquals(404, response.code());

            JsonNode json = mapper.readTree(response.body().string());
            assertEquals("Note not found", json.get("error").asText());
        });
    }

    @Test
    @Order(17)
    void notes_pagination() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/notes?limit=10&offset=5");
            assertEquals(200, response.code());

            JsonNode json = mapper.readTree(response.body().string());
            assertEquals(10, json.get("limit").asInt());
            assertEquals(5, json.get("offset").asInt());
        });
    }

    // ========================================================================
    // Profiles API
    // ========================================================================

    @Test
    @Order(20)
    void profiles_searchReturnsValidStructure() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/profiles?q=test");
            assertEquals(200, response.code());

            JsonNode json = mapper.readTree(response.body().string());
            assertTrue(json.has("total"));
            assertTrue(json.has("profiles"));
            assertTrue(json.get("profiles").isArray());
        });
    }

    @Test
    @Order(21)
    void profiles_getByPubkey_invalidPubkey() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/profiles/not-valid");
            assertEquals(400, response.code());
        });
    }

    @Test
    @Order(22)
    void profiles_getByPubkey_notFound() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/profiles/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            assertEquals(404, response.code());
        });
    }

    // ========================================================================
    // Search API
    // ========================================================================

    @Test
    @Order(30)
    void search_returnsValidStructure() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/search?q=hello");
            assertEquals(200, response.code());

            JsonNode json = mapper.readTree(response.body().string());
            assertTrue(json.has("total"));
            assertTrue(json.has("notes"));
        });
    }

    @Test
    @Order(31)
    void search_emptyQuery_returns200() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/search?q=");
            assertEquals(200, response.code());
        });
    }

    @Test
    @Order(32)
    void search_withKindFilter() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/search?q=test&kind=1");
            assertEquals(200, response.code());
        });
    }
}
