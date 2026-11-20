package com.orderplace.notification.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.OrderEventPayload;
import org.junit.jupiter.api.Test;

/** The JSON below has exactly the shape the Order Service outbox writes (an EventEnvelope with an OrderEventPayload). */
class EventParserTest {

    private final EventParser parser = new EventParser();

    private static final String VALID = """
            {"eventId":"7d1c1f0e-5a43-4f5b-9a55-0c5d0c7d7a11","eventType":"OrderSubmitted","eventVersion":1,
             "occurredAt":"2026-11-02T10:15:30.123456Z","correlationId":"abc-123",
             "aggregateId":"3f2b8d52-8c1e-4d0b-8a52-7a1f0f2f9a10",
             "payload":{"orderId":"3f2b8d52-8c1e-4d0b-8a52-7a1f0f2f9a10","orderNumber":"OP-2026-000007",
               "customerId":"11111111-1111-1111-1111-111111111111","customerName":"Demo Customer One",
               "dealerId":"22222222-2222-2222-2222-222222222222","customerEmail":"c@x.com","dealerEmail":"d@x.com",
               "total":4600.00,"currency":"INR","reason":null}}
            """;

    @Test
    void readsAnOrderEvent() {
        EventEnvelope<OrderEventPayload> event = parser.parseOrderEvent(VALID);

        assertThat(event.eventType()).isEqualTo("OrderSubmitted");
        assertThat(event.eventId().toString()).isEqualTo("7d1c1f0e-5a43-4f5b-9a55-0c5d0c7d7a11");
        assertThat(event.correlationId()).isEqualTo("abc-123");
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.payload().orderNumber()).isEqualTo("OP-2026-000007");
        assertThat(event.payload().dealerEmail()).isEqualTo("d@x.com");
        assertThat(event.payload().total()).isEqualByComparingTo("4600.00");
    }

    @Test
    void ignoresFieldsAddedByNewerVersions() {
        String withExtra = VALID.replace("\"eventVersion\":1,", "\"eventVersion\":2,\"newField\":\"x\",");

        assertThat(parser.parseOrderEvent(withExtra).eventType()).isEqualTo("OrderSubmitted");
    }

    @Test
    void garbageIsAnIllegalArgument_whichKafkaNeverRetries() {
        assertThatThrownBy(() -> parser.parseOrderEvent("this is not json")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventWithoutPayloadIsRejected() {
        String noPayload = "{\"eventId\":\"7d1c1f0e-5a43-4f5b-9a55-0c5d0c7d7a11\",\"eventType\":\"OrderSubmitted\"}";

        assertThatThrownBy(() -> parser.parseOrderEvent(noPayload)).isInstanceOf(IllegalArgumentException.class);
    }
}
