package io.smstunnel;

import java.util.Locale;

/** Where a scheduled text is, from {@link ScheduledText#getStatus()}. */
public enum ScheduledTextStatus {
    /** Waiting for its time. It can still be canceled. */
    SCHEDULED,
    /**
     * SMSTunnel sent it at its time, as if your app had called {@code sendSms} then: {@link ScheduledText#getMessageId()}
     * is the text's id, and {@link SmsTunnelClient#getMessageStatus(String)} says whether the phone sent it.
     */
    SENT,
    /** It did not go out at its time; {@link ScheduledText#getError()} says why. */
    FAILED,
    /** Canceled before its time. */
    CANCELED,
    /** A status this version does not know yet; see {@link ScheduledText#getRaw()}. */
    UNKNOWN;

    static ScheduledTextStatus of(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
