package xyz.tcheeric.nostrdb.inspector.middleware;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitMiddlewareTest {

    @Test
    void allowsRequestsWithinLimit() {
        Javalin app = Javalin.create();
        app.before(new RateLimitMiddleware(5));
        app.get("/test", ctx -> ctx.result("ok"));

        JavalinTest.test(app, (server, client) -> {
            for (int i = 0; i < 5; i++) {
                Response response = client.get("/test");
                assertEquals(200, response.code(), "Request " + (i + 1) + " should succeed");
            }
        });
    }

    @Test
    void blocksRequestsOverLimit() {
        Javalin app = Javalin.create();
        app.before(new RateLimitMiddleware(2));
        app.get("/test", ctx -> ctx.result("ok"));

        JavalinTest.test(app, (server, client) -> {
            // Consume all tokens
            client.get("/test");
            client.get("/test");

            // Third request should be rate limited
            Response response = client.get("/test");
            assertEquals(429, response.code());
        });
    }

    @Test
    void forFlush_allowsOnePerMinute() {
        RateLimitMiddleware limiter = RateLimitMiddleware.forFlush();
        assertNotNull(limiter);

        Javalin app = Javalin.create();
        app.before(limiter);
        app.post("/api/flush", ctx -> ctx.result("flushed"));

        JavalinTest.test(app, (server, client) -> {
            Response first = client.post("/api/flush");
            assertEquals(200, first.code());

            // Second should be rate limited
            Response second = client.post("/api/flush");
            assertEquals(429, second.code());
        });
    }

    @Test
    void forIngest_creates10ReqPerSecLimiter() {
        RateLimitMiddleware limiter = RateLimitMiddleware.forIngest();
        assertNotNull(limiter);

        Javalin app = Javalin.create();
        app.before(limiter);
        app.post("/api/ingest", ctx -> ctx.result("ingested"));

        JavalinTest.test(app, (server, client) -> {
            // Should allow at least a few requests
            for (int i = 0; i < 5; i++) {
                Response response = client.post("/api/ingest");
                assertEquals(200, response.code());
            }
        });
    }

    @Test
    void rateLimitResponse_returns429Status() {
        Javalin app = Javalin.create();
        app.before(new RateLimitMiddleware(1));
        app.get("/test", ctx -> ctx.result("ok"));

        JavalinTest.test(app, (server, client) -> {
            client.get("/test"); // consume the token
            Response response = client.get("/test"); // should be blocked
            assertEquals(429, response.code());
        });
    }
}
