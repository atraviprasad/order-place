package com.orderplace.order.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderplace.common.events.EventTypes;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentEventListenerTest {

    @Test
    void parsesPaymentSucceededAndIgnoresUnknownFields() {
        UUID orderId = UUID.randomUUID();
        String json = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"PaymentSucceeded\",\"eventVersion\":1,"
                + "\"occurredAt\":\"2026-11-03T10:00:00Z\",\"aggregateId\":\"" + orderId + "\",\"payload\":{\"paymentId\":\""
                + UUID.randomUUID() + "\",\"orderId\":\"" + orderId + "\",\"amount\":10.00,\"currency\":\"INR\",\"extra\":1}}";

        var e = PaymentEventListener.parse(json);

        assertThat(e.eventType()).isEqualTo(EventTypes.PAYMENT_SUCCEEDED);
        assertThat(e.payload().orderId()).isEqualTo(orderId);
    }

    @Test
    void rejectsUnreadableOrIncompleteEvents() {
        assertThatThrownBy(() -> PaymentEventListener.parse("garbage")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentEventListener.parse("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"PaymentSucceeded\",\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
