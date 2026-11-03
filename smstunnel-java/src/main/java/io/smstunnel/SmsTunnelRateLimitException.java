package io.smstunnel;

import java.time.Duration;

/**
 * 429: too many requests (30 sends a minute per key, and an account ceiling of three keys' worth), or the plan's
 * texts for the day are used up. {@link #getRetryAfter()} gives the wait when SMSTunnel says it (the per-minute
 * limit); a daily limit has none. The client never retries a 429 by itself.
 */
public class SmsTunnelRateLimitException extends SmsTunnelApiException {

    private static final long serialVersionUID = 1L;

    public SmsTunnelRateLimitException(String message, Object body, Duration retryAfter) {
        super(429, message, body, retryAfter);
    }
}
