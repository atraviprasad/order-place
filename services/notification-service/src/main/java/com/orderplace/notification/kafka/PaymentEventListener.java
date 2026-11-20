package com.orderplace.notification.kafka;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.common.events.Topics;
import com.orderplace.notification.processing.NotificationProcessor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes payment-events. Event types without a rule (such as PaymentSucceeded) are ignored by the processor. */
@Component
public class PaymentEventListener {

    private final PaymentEventParser parser;
    private final NotificationProcessor processor;

    public PaymentEventListener(PaymentEventParser parser, NotificationProcessor processor) {
        this.parser = parser;
        this.processor = processor;
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS)
    public void onPaymentEvent(String json) {
        EventEnvelope<OrderEventPayload> event = parser.parse(json);
        if (event.correlationId() != null) MDC.put("correlationId", event.correlationId());
        try {
            processor.handle(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
