package io.smstunnel;

/**
 * 403: the key is valid but may not do this. {@link #getCode()} says why: {@code PERMISSION_REQUIRED} when the key
 * lacks a permission ({@link #getPermission()} names it, for example {@code sms:inbox} for received texts, a separate
 * opt-in per key), or another code when the plan does not include the feature or support turned the key off.
 */
public class SmsTunnelPermissionException extends SmsTunnelApiException {

    private static final long serialVersionUID = 1L;

    public SmsTunnelPermissionException(String message, Object body) {
        super(403, message, body, null);
    }
}
