package io.smstunnel;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * SMSTunnel answered with an error status. {@link #getMessage()} is SMSTunnel's own explanation.
 *
 * <p>The statuses with their own subclass are 401 ({@link SmsTunnelAuthenticationException}), 403
 * ({@link SmsTunnelPermissionException}), 429 ({@link SmsTunnelRateLimitException}) and 5xx
 * ({@link SmsTunnelServerException}). Others arrive as this class, for example 400 (a number SMSTunnel cannot read, a
 * sign-in text without a code), 404 (no such message for this key), 409 and 422 (an Idempotency-Key still in progress
 * or reused for a different text).
 */
public class SmsTunnelApiException extends SmsTunnelException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final transient Object body;
    private final Duration retryAfter;

    /**
     * @param statusCode the HTTP status
     * @param message what went wrong, in words
     * @param body the decoded answer: a {@code Map} for JSON, a {@code String} otherwise, or null
     * @param retryAfter the wait SMSTunnel asked for in {@code Retry-After}, or null
     */
    public SmsTunnelApiException(int statusCode, String message, Object body, Duration retryAfter) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
        this.retryAfter = retryAfter;
    }

    /** The HTTP status, such as 400 or 404. */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * SMSTunnel's code for errors your software can act on, or null. For example {@code PERMISSION_REQUIRED} (the key
     * lacks a permission, see {@link #getPermission()}), {@code DISABLED_BY_SUPPORT}, {@code IDEMPOTENCY_KEY_REUSED} or
     * {@code IDEMPOTENCY_KEY_IN_PROGRESS}.
     */
    public String getCode() {
        return Fields.string(Fields.object(body), "code");
    }

    /** With {@code PERMISSION_REQUIRED}: the permission the key lacks, such as {@code sms:inbox}. Otherwise null. */
    public String getPermission() {
        return Fields.string(Fields.object(body), "permission");
    }

    /** How long SMSTunnel asked to wait before trying again ({@code Retry-After}), or null when it did not say. */
    public Duration getRetryAfter() {
        return retryAfter;
    }

    /** The decoded answer: an unmodifiable {@code Map} when it was JSON, the text otherwise, or null. */
    public Object getBody() {
        return body;
    }

    @Override
    public String toString() {
        String code = getCode();
        return getClass().getName() + ": HTTP " + statusCode + (code != null ? " " + code : "") + ": " + getMessage();
    }

    /** The words of an error answer, whatever its shape (same rules as the Node SDK). */
    static String messageOf(Object body, int status) {
        if (body instanceof String && !((String) body).trim().isEmpty()) {
            String text = ((String) body).trim();
            return text.length() > 300 ? text.substring(0, 300) : text;
        }
        Map<String, Object> fields = Fields.object(body);
        Object message = fields.get("message");
        if (message instanceof List && !((List<?>) message).isEmpty()) {
            StringBuilder joined = new StringBuilder();
            for (Object part : (List<?>) message) {
                if (joined.length() > 0) {
                    joined.append("; ");
                }
                joined.append(part);
            }
            return joined.toString();
        }
        if (message instanceof String && !((String) message).isEmpty()) {
            return (String) message;
        }
        String error = Fields.string(fields, "error");
        if (error != null && !error.isEmpty()) {
            return error;
        }
        return "SMSTunnel returned error: " + status;
    }

    /** The exception for an error answer: the subclass that matches the status. */
    static SmsTunnelApiException of(int status, Object body, Duration retryAfter) {
        String message = messageOf(body, status);
        if (status == 401) {
            return new SmsTunnelAuthenticationException(message, body);
        }
        if (status == 403) {
            return new SmsTunnelPermissionException(message, body);
        }
        if (status == 429) {
            return new SmsTunnelRateLimitException(message, body, retryAfter);
        }
        if (status >= 500) {
            return new SmsTunnelServerException(status, message, body, retryAfter);
        }
        return new SmsTunnelApiException(status, message, body, retryAfter);
    }
}
