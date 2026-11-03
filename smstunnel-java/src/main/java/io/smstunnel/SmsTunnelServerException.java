package io.smstunnel;

import java.time.Duration;

/**
 * 5xx: SMSTunnel (or a proxy in front of it) had a problem. The client retries reads, and sends that carry an
 * Idempotency-Key, a few times before throwing this; a send without a key is never retried, because the text may
 * already have gone out.
 */
public class SmsTunnelServerException extends SmsTunnelApiException {

    private static final long serialVersionUID = 1L;

    public SmsTunnelServerException(int statusCode, String message, Object body, Duration retryAfter) {
        super(statusCode, message, body, retryAfter);
    }
}
