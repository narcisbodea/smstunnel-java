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
 * sign-in text without a code, a time to schedule a text at that is too soon), 404 (no such message or scheduled text
 * for this key), 409 and 422 (an Idempotency-Key still in progress or reused for a different text, a scheduled text
 * that can no longer be canceled).
 *
 * <p>The {@code SCHEDULE_*} constants are the values of {@link #getCode()} for scheduled texts.
 */
public class SmsTunnelApiException extends SmsTunnelException {

    /** 400: the time to send at is less than a minute away, by SMSTunnel's clock. To send now, use {@code sendSms}. */
    public static final String SCHEDULE_TOO_SOON = "SCHEDULE_TOO_SOON";
    /** 400: the time to send at is more than 30 days away. */
    public static final String SCHEDULE_TOO_FAR = "SCHEDULE_TOO_FAR";
    /** 400: the number, the text or the time of a text to schedule could not be read. */
    public static final String SCHEDULE_INVALID_INPUT = "SCHEDULE_INVALID_INPUT";
    /** 400: the text to schedule is longer than 1600 characters. */
    public static final String SCHEDULE_TEXT_TOO_LONG = "SCHEDULE_TEXT_TOO_LONG";
    /** 400: texts to short codes and premium-rate numbers can't be sent. */
    public static final String SCHEDULE_NUMBER_NOT_ALLOWED = "SCHEDULE_NUMBER_NOT_ALLOWED";
    /** 400: the chosen phone sends only end-to-end encrypted texts, which can't be scheduled. */
    public static final String SCHEDULE_SEALED_ONLY_PHONE = "SCHEDULE_SEALED_ONLY_PHONE";
    /** 400: end-to-end encrypted texts can't be scheduled. */
    public static final String SCHEDULE_ENCRYPTED_TEXT = "SCHEDULE_ENCRYPTED_TEXT";
    /** 400: sign-in codes can't be scheduled: send them when they are asked for. */
    public static final String SCHEDULE_SIGN_IN_CODE = "SCHEDULE_SIGN_IN_CODE";
    /** 404: the chosen phone ({@code deviceId}) is not in the key's account. */
    public static final String SCHEDULE_PHONE_NOT_FOUND = "SCHEDULE_PHONE_NOT_FOUND";
    /**
     * 403 ({@link SmsTunnelPermissionException}): as many texts wait as the plan allows ({@link #getMax()}). Wait until
     * some go out, or cancel one.
     */
    public static final String SCHEDULE_LIMIT_REACHED = "SCHEDULE_LIMIT_REACHED";
    /** 404: this key scheduled no text with that id (texts that are done are kept 30 days). */
    public static final String SCHEDULE_NOT_FOUND = "SCHEDULE_NOT_FOUND";
    /** 409: the scheduled text already went out, failed or was canceled, so it can't be canceled. */
    public static final String SCHEDULE_NOT_EDITABLE = "SCHEDULE_NOT_EDITABLE";

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
     * lacks a permission, see {@link #getPermission()}), {@code DISABLED_BY_SUPPORT}, {@code IDEMPOTENCY_KEY_REUSED},
     * {@code IDEMPOTENCY_KEY_IN_PROGRESS}, or one of the {@code SCHEDULE_*} codes of this class.
     */
    public String getCode() {
        return Fields.string(Fields.object(body), "code");
    }

    /** With {@code PERMISSION_REQUIRED}: the permission the key lacks, such as {@code sms:inbox}. Otherwise null. */
    public String getPermission() {
        return Fields.string(Fields.object(body), "permission");
    }

    /**
     * With {@link #SCHEDULE_LIMIT_REACHED}: how many texts the plan lets wait at once (the answer's {@code max}).
     * Otherwise null.
     */
    public Long getMax() {
        return Fields.longValue(Fields.object(body), "max");
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
