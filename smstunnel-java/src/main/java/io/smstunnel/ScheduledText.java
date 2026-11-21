package io.smstunnel;

import java.time.Instant;
import java.util.Map;

/**
 * A text SMSTunnel keeps and sends at its time, from {@link SmsTunnelClient#getScheduledTexts(ScheduledTextsQuery)},
 * {@link SmsTunnelClient#getScheduledText(String)}, {@link SmsTunnelClient#cancelScheduledText(String)} or
 * {@link ScheduledSendResult#getScheduledText()}.
 */
public final class ScheduledText {

    private final String id;
    private final String to;
    private final String message;
    private final Instant sendAt;
    private final ScheduledTextStatus status;
    private final String deviceId;
    private final String messageId;
    private final String error;
    private final Instant createdAt;
    private final Map<String, Object> raw;

    private ScheduledText(Map<String, Object> raw) {
        this.id = Fields.string(raw, "id");
        this.to = Fields.string(raw, "to");
        this.message = Fields.string(raw, "message");
        this.sendAt = Fields.instant(raw, "sendAt");
        this.status = ScheduledTextStatus.of(Fields.string(raw, "status"));
        this.deviceId = Fields.string(raw, "deviceId");
        this.messageId = Fields.string(raw, "messageId");
        this.error = Fields.string(raw, "error");
        this.createdAt = Fields.instant(raw, "createdAt");
        this.raw = raw;
    }

    static ScheduledText from(Map<String, Object> raw) {
        return new ScheduledText(raw);
    }

    /**
     * Its id: pass it to {@link SmsTunnelClient#getScheduledText(String)} or
     * {@link SmsTunnelClient#cancelScheduledText(String)}.
     */
    public String getId() {
        return id;
    }

    /** The recipient, as it was given (a local number is completed with the country code when the text goes out). */
    public String getTo() {
        return to;
    }

    /** The words. */
    public String getMessage() {
        return message;
    }

    /** When SMSTunnel sends it. */
    public Instant getSendAt() {
        return sendAt;
    }

    /** {@link ScheduledTextStatus#SCHEDULED} while it waits, then {@code SENT}, {@code FAILED} or {@code CANCELED}. */
    public ScheduledTextStatus getStatus() {
        return status;
    }

    /** The phone it goes out from, or null when SMSTunnel picks one of your phones at its time. */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Once {@link ScheduledTextStatus#SENT}: the text's id, for {@link SmsTunnelClient#getMessageStatus(String)} (whether
     * the phone sent it). Otherwise null.
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Once {@link ScheduledTextStatus#FAILED}: why, otherwise null. One of {@code key_not_active} (the key was turned
     * off, deleted or expired, or lost {@code sms:send}), {@code limit_reached} (the plan's texts for that day were used
     * up), {@code invalid_number} (the number could not be read with the country code of that moment),
     * {@code phone_not_found}, {@code phone_unlinked}, {@code sealed_only_phone}, {@code number_not_allowed},
     * {@code account_not_active}, {@code interrupted} or {@code failed}. A later SMSTunnel may add others.
     */
    public String getError() {
        return error;
    }

    /** When it was scheduled, or null. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** This scheduled text as SMSTunnel sent it, for fields this version does not map yet. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "ScheduledText{id=" + id + ", sendAt=" + sendAt + ", status=" + status
                + (messageId != null ? ", messageId=" + messageId : "")
                + (error != null ? ", error=" + error : "") + "}";
    }
}
