package com.orderplace.notification.kafka;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.common.events.Topics;
import com.orderplace.notification.processing.NotificationProcessor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes order-events. Business services never call this service directly (FR-NOT-01). */
@Component
public class OrderEventListener {

    private final EventParser parser;
    private final NotificationProcessor processor;

    public OrderEventListener(EventParser parser, NotificationProcessor processor) {
        this.parser = parser;
        this.processor = processor;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    public void onOrderEvent(String json) {
        EventEnvelope<OrderEventPayload> event = parser.parseOrderEvent(json);
        if (event.correlationId() != null) MDC.put("correlationId", event.correlationId());
        try {
            processor.handle(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
