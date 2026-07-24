package eu.tango.scamscreener.marketguard.api;

/**
 * The Player API was reached, but its endpoint did not return a usable player result.
 */
public final class PlayerApiUnavailableException extends RuntimeException {
    private final int statusCode;

    public PlayerApiUnavailableException(String message) {
        this(message, -1, null);
    }

    public PlayerApiUnavailableException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public PlayerApiUnavailableException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    private PlayerApiUnavailableException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
