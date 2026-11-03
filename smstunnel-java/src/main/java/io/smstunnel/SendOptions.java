package io.smstunnel;

/**
 * Optional settings for one send. Immutable; build one with {@link #builder()}.
 *
 * <pre>{@code
 * SendOptions options = SendOptions.builder()
 *     .idempotencyKey("order-1042-shipped")   // a retry with this key never sends a second text
 *     .defaultCountryCode("+40")              // completes numbers written without + or 00
 *     .build();
 * }</pre>
 */
public final class SendOptions {

    private static final SendOptions NONE = new SendOptions(null, null, null);

    private final String deviceId;
    private final String defaultCountryCode;
    private final String idempotencyKey;

    private SendOptions(String deviceId, String defaultCountryCode, String idempotencyKey) {
        this.deviceId = deviceId;
        this.defaultCountryCode = defaultCountryCode;
        this.idempotencyKey = idempotencyKey;
    }

    /** No options: SMSTunnel picks one of your phones, and the client's own default country code applies. */
    public static SendOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The phone that sends the text (its id under Phones in the dashboard), or null to let SMSTunnel pick. */
    public String getDeviceId() {
        return deviceId;
    }

    /** The country code for a number written without {@code +} or {@code 00}, or null for the client's default. */
    public String getDefaultCountryCode() {
        return defaultCountryCode;
    }

    /** The Idempotency-Key sent with the request, or null. */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** Builds {@link SendOptions}. */
    public static final class Builder {
        private String deviceId;
        private String defaultCountryCode;
        private String idempotencyKey;

        private Builder() {
        }

        /** Send through this phone: its id from {@link SmsTunnelClient#getPhones()} or the dashboard (Phones). */
        public Builder deviceId(String deviceId) {
            this.deviceId = blankToNull(deviceId);
            return this;
        }

        /**
         * Completes a number written without {@code +} or {@code 00}: {@code "+49"}, {@code "49"} and {@code "0049"}
         * all mean Germany. It takes precedence over the phone's own setting and the client's default.
         */
        public Builder defaultCountryCode(String defaultCountryCode) {
            this.defaultCountryCode = SmsTunnelClient.checkCountryCode(blankToNull(defaultCountryCode));
            return this;
        }

        /**
         * A key of your own for this text, such as {@code order-1042-shipped} or a UUID: a request repeated with the
         * same key within 24 hours gets the first answer back instead of sending a second text. 1 to 255 printable
         * ASCII characters. Without one, the client makes one per call when it is allowed to retry.
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = SmsTunnelClient.checkIdempotencyKey(idempotencyKey);
            return this;
        }

        public SendOptions build() {
            return new SendOptions(deviceId, defaultCountryCode, idempotencyKey);
        }
    }

    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
