package com.orderplace.notification.kafka;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.common.events.PaymentEventPayload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a payment event and presents it in the shape the notification processor already understands, so one
 * processor, one log and one retry policy serve both topics. Only the fields an email needs are carried over.
 */
@Component
public class PaymentEventParser {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final TypeReference<EventEnvelope<PaymentEventPayload>> PAYMENT_EVENT = new TypeReference<>() {};

    public EventEnvelope<OrderEventPayload> parse(String json) {
        try {
            EventEnvelope<PaymentEventPayload> e = MAPPER.readValue(json, PAYMENT_EVENT);
            if (e == null || e.eventId() == null || e.eventType() == null || e.payload() == null) {
                throw new IllegalArgumentException("Event is missing eventId, eventType or payload");
            }
            PaymentEventPayload p = e.payload();
            OrderEventPayload asOrder = new OrderEventPayload(p.orderId(), p.orderNumber(), null, null, null,
                    p.customerEmail(), null, p.amount(), p.currency(), p.failureReason());
            return new EventEnvelope<>(e.eventId(), e.eventType(), e.eventVersion(), e.occurredAt(),
                    e.correlationId(), e.aggregateId(), asOrder);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Event is not valid JSON: " + ex.getOriginalMessage(), ex);
        }
    }
}
