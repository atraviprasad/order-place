package com.orderplace.order.outbox;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.common.events.Topics;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.repository.OutboxEventRepository;
import com.orderplace.order.model.OutboxEvent;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores an event in the outbox table. Call it inside the same transaction as the order change: either both are
 * saved or neither is, so an event is never lost and never sent for a change that was rolled back.
 */
@Component
public class OutboxWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final OutboxEventRepository outbox;

    public OutboxWriter(OutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    public void orderEvent(String eventType, CustomerOrder order, String reason) {
        OrderEventPayload payload = new OrderEventPayload(order.getId(), order.getOrderNumber(), order.getCustomerId(),
                order.getCustomerName(), order.getDealerId(), order.getCustomerEmail(), order.getDealerEmail(),
                order.getTotalAmount(), order.getCurrency(), reason);
        EventEnvelope<OrderEventPayload> envelope = EventEnvelope.of(eventType, order.getId(), MDC.get("correlationId"), payload);
        outbox.save(OutboxEvent.of(envelope.eventId(), order.getId(), eventType, Topics.ORDER_EVENTS,
                MAPPER.writeValueAsString(envelope)));
    }
}
