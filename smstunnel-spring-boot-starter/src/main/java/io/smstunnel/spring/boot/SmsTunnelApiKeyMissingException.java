package io.smstunnel.spring.boot;

/** Thrown at startup when the starter is on the classpath and turned on, but {@code smstunnel.api-key} is not set. */
public class SmsTunnelApiKeyMissingException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public SmsTunnelApiKeyMissingException() {
        super("smstunnel.api-key is not set");
    }
}
