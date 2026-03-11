package xyz.tcheeric.nostrdb.inspector.middleware;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthMiddlewareTest {

    private static final String API_KEY = "test-secret-key";

    private Javalin createApp() {
        Javalin app = Javalin.create();
        app.before(new AuthMiddleware(API_KEY));
        app.get("/api/test", ctx -> ctx.result("ok"));
        app.get("/static/style.css", ctx -> ctx.result("css"));
        return app;
    }

    @Test
    void validApiKey_allows() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/test", req ->
                    req.addHeader("X-API-Key", API_KEY));
            assertEquals(200, response.code());
            assertEquals("ok", response.body().string());
        });
    }

    @Test
    void missingApiKey_returns401() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/test");
            assertEquals(401, response.code());
        });
    }

    @Test
    void wrongApiKey_returns401() {
        JavalinTest.test(createApp(), (server, client) -> {
            Response response = client.get("/api/test", req ->
                    req.addHeader("X-API-Key", "wrong-key"));
            assertEquals(401, response.code());
        });
    }

    @Test
    void staticAssets_skipAuth() {
        JavalinTest.test(createApp(), (server, client) -> {
            // Static paths should bypass auth even without API key
            Response response = client.get("/static/style.css");
            assertEquals(200, response.code());
            assertEquals("css", response.body().string());
        });
    }
}
