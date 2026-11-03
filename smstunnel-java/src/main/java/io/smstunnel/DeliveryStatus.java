package io.smstunnel;

import java.util.Locale;

/** Where a text is now. */
public enum DeliveryStatus {
    /** Waiting to go out (the phone may be offline). */
    PENDING,
    /** The phone sent it. */
    SENT,
    /** The carrier confirmed delivery. */
    DELIVERED,
    /** It did not go out; {@link MessageStatus#getErrorMessage()} says why. */
    FAILED,
    /** Handed to the SIM, but the phone got no confirmation. Final, never retried. */
    UNCONFIRMED,
    /** A state this version does not know yet; see {@link MessageStatus#getRaw()}. */
    UNKNOWN;

    static DeliveryStatus of(String value) {
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
