package com.orderplace.payment.outbox;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.PaymentEventPayload;
import com.orderplace.common.events.Topics;
import com.orderplace.payment.model.OutboxEvent;
import com.orderplace.payment.model.Payment;
import com.orderplace.payment.repository.OutboxEventRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Stores a payment event in the outbox, in the same transaction as the payment change. The Kafka key is the order id. */
@Component
public class OutboxWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final OutboxEventRepository outbox;

    public OutboxWriter(OutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    public void paymentEvent(String eventType, Payment p) {
        PaymentEventPayload payload = new PaymentEventPayload(p.getId(), p.getOrderId(), p.getOrderNumber(),
                p.getPayerEmail(), p.getAmount(), p.getCurrency(), p.getFailureReason());
        EventEnvelope<PaymentEventPayload> envelope = EventEnvelope.of(eventType, p.getOrderId(), MDC.get("correlationId"), payload);
        outbox.save(OutboxEvent.of(envelope.eventId(), p.getOrderId(), eventType, Topics.PAYMENT_EVENTS,
                MAPPER.writeValueAsString(envelope)));
    }
}
