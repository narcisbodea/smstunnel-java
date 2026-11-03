package io.smstunnel;

/**
 * 401: the API key is wrong, was deleted, or its account is suspended. Check the key in your dashboard
 * (https://dashboard.smstunnel.io/apps). Retrying with the same key does not help.
 */
public class SmsTunnelAuthenticationException extends SmsTunnelApiException {

    private static final long serialVersionUID = 1L;

    public SmsTunnelAuthenticationException(String message, Object body) {
        super(401, message, body, null);
    }
}
