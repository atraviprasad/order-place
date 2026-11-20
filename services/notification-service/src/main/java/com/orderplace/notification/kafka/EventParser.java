package com.orderplace.notification.kafka;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.OrderEventPayload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Reads the event envelope written by the Order Service. A message that cannot be read is never retried. */
@Component
public class EventParser {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)   // newer event versions may add fields
            .build();
    private static final TypeReference<EventEnvelope<OrderEventPayload>> ORDER_EVENT = new TypeReference<>() {};

    public EventEnvelope<OrderEventPayload> parseOrderEvent(String json) {
        try {
            EventEnvelope<OrderEventPayload> event = MAPPER.readValue(json, ORDER_EVENT);
            if (event == null || event.eventId() == null || event.eventType() == null || event.payload() == null) {
                throw new IllegalArgumentException("Event is missing eventId, eventType or payload");
            }
            return event;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Event is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }
}
