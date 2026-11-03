package io.smstunnel;

import java.time.Instant;

/** Which received texts to read. Immutable; build one with {@link #builder()}. Every field is optional. */
public final class ReceivedTextsQuery {

    private final Integer limit;
    private final Integer page;
    private final Instant since;
    private final String phone;
    private final String deviceId;

    private ReceivedTextsQuery(Builder b) {
        this.limit = b.limit;
        this.page = b.page;
        this.since = b.since;
        this.phone = b.phone;
        this.deviceId = b.deviceId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The newest 50 texts. */
    public static ReceivedTextsQuery latest() {
        return new Builder().build();
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getPage() {
        return page;
    }

    public Instant getSince() {
        return since;
    }

    public String getPhone() {
        return phone;
    }

    public String getDeviceId() {
        return deviceId;
    }

    /** Builds {@link ReceivedTextsQuery}. */
    public static final class Builder {
        private Integer limit;
        private Integer page;
        private Instant since;
        private String phone;
        private String deviceId;

        private Builder() {
        }

        /** Texts per page, 1 to 100 (SMSTunnel's default is 50). */
        public Builder limit(int limit) {
            if (limit < 1 || limit > 100) {
                throw new IllegalArgumentException("limit must be between 1 and 100");
            }
            this.limit = limit;
            return this;
        }

        /** The page, from 1. */
        public Builder page(int page) {
            if (page < 1) {
                throw new IllegalArgumentException("page starts at 1");
            }
            this.page = page;
            return this;
        }

        /** Only texts received at or after this time. */
        public Builder since(Instant since) {
            this.since = since;
            return this;
        }

        /** Only texts from this sender (a part of the number is enough). */
        public Builder phone(String phone) {
            this.phone = SendOptions.blankToNull(phone);
            return this;
        }

        /** Only texts received by this phone. */
        public Builder deviceId(String deviceId) {
            this.deviceId = SendOptions.blankToNull(deviceId);
            return this;
        }

        public ReceivedTextsQuery build() {
            return new ReceivedTextsQuery(this);
        }
    }
}
