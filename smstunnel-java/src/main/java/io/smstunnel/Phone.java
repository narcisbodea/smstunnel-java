package io.smstunnel;

import java.time.Instant;
import java.util.Map;

/** A phone linked to your SMSTunnel account, from {@link SmsTunnelClient#getPhones()}. */
public final class Phone {

    private final String id;
    private final String name;
    private final boolean online;
    private final Instant lastSeenAt;
    private final Map<String, Object> raw;

    private Phone(Map<String, Object> raw) {
        this.id = Fields.string(raw, "id");
        this.name = Fields.string(raw, "name");
        this.online = Fields.bool(raw, "isOnline");
        this.lastSeenAt = Fields.instant(raw, "lastSeenAt");
        this.raw = raw;
    }

    static Phone from(Map<String, Object> raw) {
        return new Phone(raw);
    }

    /** The phone's id: pass it as {@code deviceId} to send through this phone. */
    public String getId() {
        return id;
    }

    /** The name shown under Phones in the dashboard. */
    public String getName() {
        return name;
    }

    /** True when the phone is connected now. Texts for an offline phone wait until it is back. */
    public boolean isOnline() {
        return online;
    }

    /** When SMSTunnel last heard from the phone, or null. */
    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    /** The whole entry, for fields this version does not map yet. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "Phone{id=" + id + ", name=" + name + ", online=" + online + "}";
    }
}
