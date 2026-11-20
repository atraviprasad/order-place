package com.orderplace.order.kafka;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.PaymentEventPayload;
import com.orderplace.common.events.Topics;
import com.orderplace.order.service.PaymentEventHandler;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Consumes payment-events. A message that cannot be read throws IllegalArgumentException, which is not retried. */
@Component
public class PaymentEventListener {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final TypeReference<EventEnvelope<PaymentEventPayload>> TYPE = new TypeReference<>() {};

    private final PaymentEventHandler handler;

    public PaymentEventListener(PaymentEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS)
    public void onPaymentEvent(String json) {
        EventEnvelope<PaymentEventPayload> event = parse(json);
        if (event.correlationId() != null) MDC.put("correlationId", event.correlationId());
        try {
            handler.handle(event);
        } finally {
            MDC.remove("correlationId");
        }
    }

    static EventEnvelope<PaymentEventPayload> parse(String json) {
        try {
            EventEnvelope<PaymentEventPayload> e = MAPPER.readValue(json, TYPE);
            if (e == null || e.eventId() == null || e.eventType() == null || e.payload() == null || e.payload().orderId() == null) {
                throw new IllegalArgumentException("Event is missing eventId, eventType, payload or orderId");
            }
            return e;
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Event is not valid JSON: " + ex.getOriginalMessage(), ex);
        }
    }
}
