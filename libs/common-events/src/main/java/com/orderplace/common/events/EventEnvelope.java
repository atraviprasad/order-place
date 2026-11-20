package com.orderplace.common.events;

import java.time.Instant;
import java.util.UUID;

/** Standard envelope for every Kafka event (NFR-KFK-01). Kafka key = aggregateId. */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        UUID aggregateId,
        T payload) {

    public static <T> EventEnvelope<T> of(String eventType, UUID aggregateId, String correlationId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, Instant.now(), correlationId, aggregateId, payload);
    }
}
