package io.smstunnel;

import java.time.Instant;
import java.util.Map;

/** The status of a text, from {@link SmsTunnelClient#getMessageStatus(String)}. */
public final class MessageStatus {

    private final String id;
    private final String phoneNumber;
    private final String status;
    private final DeliveryStatus deliveryStatus;
    private final boolean unconfirmed;
    private final Instant sentAt;
    private final Instant deliveredAt;
    private final String errorMessage;
    private final Map<String, Object> raw;

    private MessageStatus(Map<String, Object> raw) {
        Map<String, Object> data = Fields.object(raw, "data");
        this.id = Fields.string(data, "id");
        this.phoneNumber = Fields.string(data, "phoneNumber");
        this.status = Fields.string(data, "status");
        this.unconfirmed = Fields.bool(data, "unconfirmed");
        String real = Fields.string(data, "deliveryStatus");
        DeliveryStatus state = DeliveryStatus.of(real != null ? real : status);
        this.deliveryStatus = unconfirmed && state != DeliveryStatus.UNCONFIRMED && real == null
                ? DeliveryStatus.UNCONFIRMED
                : state;
        this.sentAt = Fields.instant(data, "sentAt");
        this.deliveredAt = Fields.instant(data, "deliveredAt");
        this.errorMessage = Fields.string(data, "errorMessage");
        this.raw = raw;
    }

    static MessageStatus from(Map<String, Object> raw) {
        return new MessageStatus(raw);
    }

    /** The text's id. */
    public String getId() {
        return id;
    }

    /** The recipient, in international format. */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * {@code pending}, {@code sent}, {@code delivered} or {@code failed}, as older integrations read it: an unconfirmed
     * text shows here as {@code failed}. {@link #getDeliveryStatus()} has the real state.
     */
    public String getStatus() {
        return status;
    }

    /** The real state, including {@link DeliveryStatus#UNCONFIRMED}. */
    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    /** True when the phone handed the text to the SIM but got no confirmation. */
    public boolean isUnconfirmed() {
        return unconfirmed || deliveryStatus == DeliveryStatus.UNCONFIRMED;
    }

    /** True once the status will not change any more: delivered, failed or unconfirmed. */
    public boolean isFinal() {
        return deliveryStatus == DeliveryStatus.DELIVERED
                || deliveryStatus == DeliveryStatus.FAILED
                || deliveryStatus == DeliveryStatus.UNCONFIRMED;
    }

    /** When the phone sent it, or null. */
    public Instant getSentAt() {
        return sentAt;
    }

    /** When the carrier confirmed delivery, or null. */
    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    /** Why it failed, or null. */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** The whole answer, for fields this version does not map yet. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "MessageStatus{id=" + id + ", status=" + deliveryStatus
                + (errorMessage != null ? ", errorMessage=" + errorMessage : "") + "}";
    }
}
