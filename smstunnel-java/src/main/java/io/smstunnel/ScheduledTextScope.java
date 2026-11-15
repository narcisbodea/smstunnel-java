package io.smstunnel;

import java.util.Locale;

/** Which scheduled texts {@link SmsTunnelClient#getScheduledTexts(ScheduledTextsQuery)} lists. */
public enum ScheduledTextScope {
    /** The texts still waiting for their time, soonest first (SMSTunnel's default). */
    WAITING,
    /** The texts that went out, failed or were canceled, newest first. SMSTunnel keeps them for 30 days. */
    DONE;

    /** The value of the {@code scope} parameter. */
    String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
