package io.smstunnel;

import java.util.Map;

/**
 * Today's usage and allowance, from {@link SmsTunnelClient#getUsage()}. Daily counts restart at 00:00 UTC. A client
 * of a company account shares the company's allowance and sees it here.
 *
 * <p>The maps keep SMSTunnel's names: {@code smsClean} (texts), {@code twoFaClean} (sign-in codes),
 * {@code smsCleanMonth}, {@code smsBranded}; a value is a number or the text {@code "unlimited"}.
 */
public final class Usage {

    /** What {@link #getRemainingTexts()} and {@link #getRemainingSignInCodes()} return when there is no limit. */
    public static final long UNLIMITED = Long.MAX_VALUE;

    private final String plan;
    private final String accountType;
    private final Map<String, Object> usage;
    private final Map<String, Object> limits;
    private final Map<String, Object> remaining;
    private final Map<String, Object> enterprise;
    private final Map<String, Object> raw;

    private Usage(Map<String, Object> raw) {
        Map<String, Object> data = Fields.object(raw, "data");
        this.plan = Fields.string(data, "plan");
        this.accountType = Fields.string(data, "accountType");
        this.usage = Fields.object(data, "usage");
        this.limits = Fields.object(data, "limits");
        this.remaining = Fields.object(data, "remaining");
        this.enterprise = Fields.objectOrNull(data, "enterprise");
        this.raw = raw;
    }

    static Usage from(Map<String, Object> raw) {
        return new Usage(raw);
    }

    /** The plan, such as {@code free} or {@code pro}. */
    public String getPlan() {
        return plan;
    }

    /** The account type, such as {@code standard}. */
    public String getAccountType() {
        return accountType;
    }

    /** Counted today: {@code smsClean}, {@code smsBranded}, {@code twoFaClean}, {@code smsCleanMonth}. */
    public Map<String, Object> getUsage() {
        return usage;
    }

    /** The plan's limits: numbers, or {@code "unlimited"}. */
    public Map<String, Object> getLimits() {
        return limits;
    }

    /** What is left: numbers, or {@code "unlimited"}. */
    public Map<String, Object> getRemaining() {
        return remaining;
    }

    /** Texts that can still go out today, or {@link #UNLIMITED}. */
    public long getRemainingTexts() {
        return amount(remaining.get("smsClean"));
    }

    /** Sign-in codes that can still go out today, or {@link #UNLIMITED}. */
    public long getRemainingSignInCodes() {
        return amount(remaining.get("twoFaClean"));
    }

    /** For a client of a company account: the company ({@code id}, {@code name}). Otherwise null. */
    public Map<String, Object> getEnterprise() {
        return enterprise;
    }

    /** The whole answer, for fields this version does not map yet. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    private static long amount(Object value) {
        if (value instanceof Number) {
            long n = ((Number) value).longValue();
            return n < 0 ? UNLIMITED : n;
        }
        return UNLIMITED;
    }

    @Override
    public String toString() {
        return "Usage{plan=" + plan + ", usage=" + usage + ", remaining=" + remaining + "}";
    }
}
