package io.smstunnel;

import java.util.Map;

/** What SMSTunnel answered for a text it accepted. */
public final class SendResult {

    private final String messageId;
    private final boolean queued;
    private final boolean branded;
    private final boolean replayed;
    private final Map<String, Object> remaining;
    private final Map<String, Object> raw;

    private SendResult(Map<String, Object> raw, boolean replayed) {
        Map<String, Object> data = Fields.object(raw, "data");
        String id = Fields.string(raw, "messageId");
        this.messageId = id != null ? id : Fields.string(data, "messageId");
        this.queued = Fields.bool(raw, "queued") || Fields.bool(data, "queued");
        this.branded = Fields.bool(data, "branded");
        this.remaining = Fields.object(data, "remaining");
        this.replayed = replayed;
        this.raw = raw;
    }

    static SendResult from(Map<String, Object> raw, boolean replayed) {
        return new SendResult(raw, replayed);
    }

    /** The text's id: keep it to look up the status with {@link SmsTunnelClient#getMessageStatus(String)}. */
    public String getMessageId() {
        return messageId;
    }

    /** True when the phone was offline: the text waits and goes out as soon as the phone is back online. */
    public boolean isQueued() {
        return queued;
    }

    /** True when the text carries a short SMSTunnel signature (the free allowance of the Free plan). */
    public boolean isBranded() {
        return branded;
    }

    /**
     * True when this answer is the stored answer to an earlier request with the same Idempotency-Key: no new text
     * was sent.
     */
    public boolean isReplayed() {
        return replayed;
    }

    /** Today's allowance left after this text, as SMSTunnel reports it, for example {@code {clean=95, total=unlimited}}. */
    public Map<String, Object> getRemaining() {
        return remaining;
    }

    /** The whole answer, for fields this version does not map yet. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "SendResult{messageId=" + messageId + ", queued=" + queued + ", branded=" + branded
                + (replayed ? ", replayed=true" : "") + "}";
    }
}
