package io.smstunnel;

/**
 * Which scheduled texts to list. Immutable; build one with {@link #builder()}, or use {@link #waiting()} or
 * {@link #done()}. Every field is optional.
 */
public final class ScheduledTextsQuery {

    private final ScheduledTextScope scope;
    private final Integer page;
    private final Integer limit;

    private ScheduledTextsQuery(Builder b) {
        this.scope = b.scope;
        this.page = b.page;
        this.limit = b.limit;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The first 50 texts still waiting, soonest first (what SMSTunnel lists by default). */
    public static ScheduledTextsQuery waiting() {
        return new Builder().build();
    }

    /** The newest 50 texts that went out, failed or were canceled. */
    public static ScheduledTextsQuery done() {
        return new Builder().scope(ScheduledTextScope.DONE).build();
    }

    /** The scope, or null for SMSTunnel's default ({@link ScheduledTextScope#WAITING}). */
    public ScheduledTextScope getScope() {
        return scope;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getLimit() {
        return limit;
    }

    /** Builds {@link ScheduledTextsQuery}. */
    public static final class Builder {
        private ScheduledTextScope scope;
        private Integer page;
        private Integer limit;

        private Builder() {
        }

        /** {@link ScheduledTextScope#WAITING} or {@link ScheduledTextScope#DONE}; null for SMSTunnel's default (waiting). */
        public Builder scope(ScheduledTextScope scope) {
            this.scope = scope;
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

        /** Texts per page, 1 to 100 (SMSTunnel's default is 50). */
        public Builder limit(int limit) {
            if (limit < 1 || limit > 100) {
                throw new IllegalArgumentException("limit must be between 1 and 100");
            }
            this.limit = limit;
            return this;
        }

        public ScheduledTextsQuery build() {
            return new ScheduledTextsQuery(this);
        }
    }
}
