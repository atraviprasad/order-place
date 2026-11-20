package com.orderplace.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.EventTypes;
import com.orderplace.common.events.PaymentEventPayload;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.LineSpec;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.model.OrderStatusHistory;
import com.orderplace.order.model.ProcessedEvent;
import com.orderplace.order.outbox.OutboxWriter;
import com.orderplace.order.repository.OrderRepository;
import com.orderplace.order.repository.OrderStatusHistoryRepository;
import com.orderplace.order.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {

    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository history;
    @Mock ProcessedEventRepository processed;
    @Mock OutboxWriter outbox;

    private PaymentEventHandler handler;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new PaymentEventHandler(orders, history, processed, outbox);
    }

    private CustomerOrder order(OrderStatus target) {
        CustomerOrder o = CustomerOrder.newDraft(UUID.randomUUID(), "Customer", "c@x.com", UUID.randomUUID(), "Address");
        o.saveDraft(UUID.randomUUID(), "Dealer", "d@x.com", "Address", null,
                List.of(new LineSpec(UUID.randomUUID(), "CHR-1", "Chair", new BigDecimal("100.00"), 10)));
        ReflectionTestUtils.setField(o, "id", orderId);
        o.submit("OP-2026-000001", "k", Instant.now());
        o.startReview();
        if (target == OrderStatus.AWAITING_PAYMENT || target == OrderStatus.CANCELLED) o.moveTo(OrderStatus.AWAITING_PAYMENT);
        if (target == OrderStatus.CANCELLED) o.cancel();
        if (target == OrderStatus.PAID) { o.moveTo(OrderStatus.AWAITING_PAYMENT); o.moveTo(OrderStatus.PAID); }
        if (target == OrderStatus.READY_FOR_DELIVERY) {
            o.moveTo(OrderStatus.AWAITING_PAYMENT); o.moveTo(OrderStatus.PAID); o.moveTo(OrderStatus.READY_FOR_DELIVERY);
        }
        return o;
    }

    private EventEnvelope<PaymentEventPayload> event(String type) {
        PaymentEventPayload p = new PaymentEventPayload(UUID.randomUUID(), orderId, "OP-2026-000001", "c@x.com",
                new BigDecimal("1000.00"), "INR", "Card declined");
        return EventEnvelope.of(type, orderId, "corr", p);
    }

    @Test
    void succeededMovesAwaitingPaymentOrderToReadyForDeliveryWithTwoSystemHistoryRows() {
        CustomerOrder o = order(OrderStatus.AWAITING_PAYMENT);
        EventEnvelope<PaymentEventPayload> e = event(EventTypes.PAYMENT_SUCCEEDED);
        when(processed.existsById(e.eventId())).thenReturn(false);
        when(orders.findById(orderId)).thenReturn(Optional.of(o));

        handler.handle(e);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.READY_FOR_DELIVERY);
        ArgumentCaptor<OrderStatusHistory> h = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history, times(2)).save(h.capture());
        assertThat(h.getAllValues()).extracting(OrderStatusHistory::getToStatus)
                .containsExactly(OrderStatus.PAID, OrderStatus.READY_FOR_DELIVERY);
        assertThat(h.getAllValues()).allSatisfy(x -> {
            assertThat(x.getChangedByRole()).isEqualTo("SYSTEM");
            assertThat(x.getChangedByUserId()).isNull();
        });
        verify(outbox).orderEvent(EventTypes.ORDER_READY_FOR_DELIVERY, o, null);
        ArgumentCaptor<ProcessedEvent> p = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processed).save(p.capture());
        assertThat(p.getValue().getEventId()).isEqualTo(e.eventId());
    }

    @Test
    void alreadyProcessedEventIsSkipped() {
        EventEnvelope<PaymentEventPayload> e = event(EventTypes.PAYMENT_SUCCEEDED);
        when(processed.existsById(e.eventId())).thenReturn(true);

        handler.handle(e);

        verify(orders, never()).findById(any());
        verify(outbox, never()).orderEvent(any(), any(), any());
        verify(processed, never()).save(any());
    }

    @Test
    void succeededForAlreadyPaidOrderChangesNothingButIsMarkedProcessed() {
        CustomerOrder o = order(OrderStatus.READY_FOR_DELIVERY);
        EventEnvelope<PaymentEventPayload> e = event(EventTypes.PAYMENT_SUCCEEDED);
        when(processed.existsById(e.eventId())).thenReturn(false);
        when(orders.findById(orderId)).thenReturn(Optional.of(o));

        handler.handle(e);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.READY_FOR_DELIVERY);
        verify(history, never()).save(any());
        verify(outbox, never()).orderEvent(any(), any(), any());
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void succeededForCancelledOrderLeavesItCancelled() {
        CustomerOrder o = order(OrderStatus.CANCELLED);
        EventEnvelope<PaymentEventPayload> e = event(EventTypes.PAYMENT_SUCCEEDED);
        when(processed.existsById(e.eventId())).thenReturn(false);
        when(orders.findById(orderId)).thenReturn(Optional.of(o));

        handler.handle(e);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(history, never()).save(any());
        verify(outbox, never()).orderEvent(any(), any(), any());
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void succeededForUnknownOrderIsMarkedProcessed() {
        EventEnvelope<PaymentEventPayload> e = event(EventTypes.PAYMENT_SUCCEEDED);
        when(processed.existsById(e.eventId())).thenReturn(false);
        when(orders.findById(orderId)).thenReturn(Optional.empty());

        handler.handle(e);

        verify(processed).save(any(ProcessedEvent.class));
        verify(outbox, never()).orderEvent(any(), any(), any());
    }

    @Test
    void failedLeavesOrderAwaitingPayment() {
        EventEnvelope<PaymentEventPayload> e = event(EventTypes.PAYMENT_FAILED);
        when(processed.existsById(e.eventId())).thenReturn(false);

        handler.handle(e);

        verify(orders, never()).findById(any());
        verify(history, never()).save(any());
        verify(outbox, never()).orderEvent(eq(EventTypes.ORDER_READY_FOR_DELIVERY), any(), any());
        verify(processed).save(any(ProcessedEvent.class));
    }
}
