package com.orderplace.order.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderplace.order.model.OutboxEvent;
import com.orderplace.order.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock OutboxEventRepository outbox;
    @Mock KafkaTemplate<String, String> kafka;
    @InjectMocks OutboxRelay relay;

    private static OutboxEvent event(String type) {
        return OutboxEvent.of(UUID.randomUUID(), UUID.randomUUID(), type, "order-events", "{\"x\":1}");
    }

    @Test
    void publishesInOrderAndMarksEachEvent() {
        OutboxEvent first = event("OrderSubmitted");
        OutboxEvent second = event("OrderCancelled");
        when(outbox.findUnpublished(any(Pageable.class))).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        relay.publishPending();

        InOrder order = inOrder(kafka, outbox);
        order.verify(kafka).send("order-events", first.getAggregateId().toString(), "{\"x\":1}");
        order.verify(outbox).markPublished(eq(first.getId()), any(Instant.class));
        order.verify(kafka).send("order-events", second.getAggregateId().toString(), "{\"x\":1}");
        order.verify(outbox).markPublished(eq(second.getId()), any(Instant.class));
    }

    @Test
    void failureStopsTheBatchSoOrderIsKept() {
        OutboxEvent first = event("OrderSubmitted");
        OutboxEvent second = event("OrderCancelled");
        when(outbox.findUnpublished(any(Pageable.class))).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.publishPending();

        verify(outbox).markFailed(eq(first.getId()), anyString());
        verify(outbox, never()).markPublished(any(), any());
        verify(kafka, never()).send(anyString(), eq(second.getAggregateId().toString()), anyString());
    }

    @Test
    void nothingPending_doesNothing() {
        when(outbox.findUnpublished(any(Pageable.class))).thenReturn(List.of());

        relay.publishPending();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
