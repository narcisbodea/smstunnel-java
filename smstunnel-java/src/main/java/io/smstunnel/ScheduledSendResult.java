package io.smstunnel;

import java.time.Instant;
import java.util.Map;

/**
 * What SMSTunnel answered for a text it scheduled, from {@link SmsTunnelClient#scheduleSms(String, String, Instant)}.
 * Nothing has been sent yet: SMSTunnel sends the text at {@link #getSendAt()}.
 */
public final class ScheduledSendResult {

    private final String scheduledId;
    private final Instant sendAt;
    private final ScheduledText scheduledText;
    private final boolean replayed;
    private final Map<String, Object> raw;

    private ScheduledSendResult(Map<String, Object> raw, boolean replayed) {
        this.scheduledText = ScheduledText.from(Fields.object(raw, "data"));
        String id = Fields.string(raw, "scheduledId");
        this.scheduledId = id != null ? id : scheduledText.getId();
        Instant at = Fields.instant(raw, "sendAt");
        this.sendAt = at != null ? at : scheduledText.getSendAt();
        this.replayed = replayed;
        this.raw = raw;
    }

    static ScheduledSendResult from(Map<String, Object> raw, boolean replayed) {
        return new ScheduledSendResult(raw, replayed);
    }

    /**
     * The scheduled text's id: keep it to look the text up with {@link SmsTunnelClient#getScheduledText(String)} or to
     * cancel it with {@link SmsTunnelClient#cancelScheduledText(String)}.
     */
    public String getScheduledId() {
        return scheduledId;
    }

    /** When SMSTunnel sends the text, as SMSTunnel stored it. */
    public Instant getSendAt() {
        return sendAt;
    }

    /**
     * {@link ScheduledTextStatus#SCHEDULED}. In a replayed answer ({@link #isReplayed()}) it is the status the text had
     * when it was first scheduled; {@link SmsTunnelClient#getScheduledText(String)} gives the current one.
     */
    public ScheduledTextStatus getStatus() {
        return scheduledText.getStatus();
    }

    /** The whole scheduled text: the number, the words, the phone and the times. */
    public ScheduledText getScheduledText() {
        return scheduledText;
    }

    /**
     * True when this answer is the stored answer to an earlier request with the same Idempotency-Key: no second text
     * was scheduled.
     */
    public boolean isReplayed() {
        return replayed;
    }

    /** The whole answer, for fields this version does not map yet. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "ScheduledSendResult{scheduledId=" + scheduledId + ", sendAt=" + sendAt + ", status=" + getStatus()
                + (replayed ? ", replayed=true" : "") + "}";
    }
}
