package io.smstunnel;

/**
 * SMSTunnel could not be reached (DNS, TLS, a refused connection), did not answer within the timeout, or the thread
 * was interrupted while waiting. For a send, the text may or may not have been accepted: send it again only with the
 * same Idempotency-Key, which makes a second attempt return the first answer instead of a second text.
 */
public class SmsTunnelConnectionException extends SmsTunnelException {

    private static final long serialVersionUID = 1L;

    private final boolean timeout;

    public SmsTunnelConnectionException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    /** True when SMSTunnel did not answer in time (the request may still be processed). */
    public boolean isTimeout() {
        return timeout;
    }
}
