package io.smstunnel;

/**
 * The base of every exception this library throws when a call does not go through. It is unchecked.
 *
 * <ul>
 *   <li>{@link SmsTunnelApiException}: SMSTunnel answered with an error status. Its subclasses are
 *       {@link SmsTunnelAuthenticationException} (401), {@link SmsTunnelPermissionException} (403),
 *       {@link SmsTunnelRateLimitException} (429) and {@link SmsTunnelServerException} (5xx).</li>
 *   <li>{@link SmsTunnelConnectionException}: SMSTunnel could not be reached, or did not answer in time.</li>
 * </ul>
 */
public class SmsTunnelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SmsTunnelException(String message) {
        super(message);
    }

    public SmsTunnelException(String message, Throwable cause) {
        super(message, cause);
    }
}
