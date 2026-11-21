package io.smstunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MessageStatusTest {

    private static MessageStatus withState(String state) {
        return MessageStatus.from(Map.of("data", Map.of("id", "m1", "deliveryStatus", state)));
    }

    @Test
    void knowsWhichStatesAreFinal() {
        Map<DeliveryStatus, Boolean> expected = new EnumMap<>(DeliveryStatus.class);
        expected.put(DeliveryStatus.PENDING, false);
        expected.put(DeliveryStatus.SENT, true);
        expected.put(DeliveryStatus.DELIVERED, true);
        expected.put(DeliveryStatus.FAILED, true);
        expected.put(DeliveryStatus.UNCONFIRMED, true);
        expected.put(DeliveryStatus.UNKNOWN, false);

        assertEquals(EnumSet.allOf(DeliveryStatus.class), expected.keySet());
        expected.forEach((state, isFinal) -> {
            MessageStatus status = withState(state.name().toLowerCase(Locale.ROOT));

            assertEquals(state, status.getDeliveryStatus());
            assertEquals(isFinal, status.isFinal(), state.name());
        });
    }

    @Test
    void aStateThisVersionDoesNotKnowIsNotFinal() {
        MessageStatus status = withState("read-by-martians");

        assertEquals(DeliveryStatus.UNKNOWN, status.getDeliveryStatus());
        assertFalse(status.isFinal());
    }
}
