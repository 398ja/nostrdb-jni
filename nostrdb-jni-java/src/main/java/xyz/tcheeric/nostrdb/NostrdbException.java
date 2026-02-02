package xyz.tcheeric.nostrdb;

/**
 * Exception thrown by nostrdb operations.
 */
public final class NostrdbException extends RuntimeException {

    public NostrdbException(String message) {
        super(message);
    }

    public NostrdbException(String message, Throwable cause) {
        super(message, cause);
    }

    public NostrdbException(Throwable cause) {
        super(cause);
    }
}
