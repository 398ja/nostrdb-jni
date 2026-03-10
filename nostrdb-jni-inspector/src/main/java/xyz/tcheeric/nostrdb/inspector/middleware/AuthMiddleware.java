package xyz.tcheeric.nostrdb.inspector.middleware;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;

/**
 * API key authentication middleware.
 * <p>
 * When an API key is configured, all requests must include an {@code X-API-Key} header
 * with the correct key. Static assets are excluded from authentication.
 */
public class AuthMiddleware implements Handler {

    private final String apiKey;

    public AuthMiddleware(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void handle(Context ctx) {
        // Skip auth for static assets
        if (ctx.path().startsWith("/static/")) {
            return;
        }

        String providedKey = ctx.header("X-API-Key");
        if (providedKey == null || !providedKey.equals(apiKey)) {
            ctx.status(HttpStatus.UNAUTHORIZED)
               .json(java.util.Map.of("error", "Invalid or missing API key"));
        }
    }
}
