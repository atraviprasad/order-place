package com.orderplace.order.service;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.EventTypes;
import com.orderplace.common.events.PaymentEventPayload;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.model.OrderStatusHistory;
import com.orderplace.order.model.ProcessedEvent;
import com.orderplace.order.outbox.OutboxWriter;
import com.orderplace.order.repository.OrderRepository;
import com.orderplace.order.repository.OrderStatusHistoryRepository;
import com.orderplace.order.repository.ProcessedEventRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies payment results to orders. One transaction per event: the order change, its history rows, the outbox
 * event and the "already processed" marker are saved together or not at all.
 * <ul>
 *   <li>PaymentSucceeded on an AWAITING_PAYMENT order: PAID, then READY_FOR_DELIVERY, both recorded as SYSTEM.</li>
 *   <li>PaymentSucceeded on an order that is already paid: nothing to do.</li>
 *   <li>PaymentSucceeded on any other order (for example cancelled meanwhile): the money was taken but the order
 *       cannot move, so an ERROR line asks for a manual refund.</li>
 *   <li>PaymentFailed: the order stays AWAITING_PAYMENT so the customer can pay again.</li>
 * </ul>
 */
@Service
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);
    private static final String SYSTEM = "SYSTEM";

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ProcessedEventRepository processed;
    private final OutboxWriter outbox;

    public PaymentEventHandler(OrderRepository orders, OrderStatusHistoryRepository history,
                               ProcessedEventRepository processed, OutboxWriter outbox) {
        this.orders = orders;
        this.history = history;
        this.processed = processed;
        this.outbox = outbox;
    }

    @Transactional
    public void handle(EventEnvelope<PaymentEventPayload> event) {
        if (processed.existsById(event.eventId())) {
            log.info("Payment event {} already processed, skipping", event.eventId());
            return;
        }
        PaymentEventPayload p = event.payload();
        switch (event.eventType()) {
            case EventTypes.PAYMENT_SUCCEEDED -> onSucceeded(p);
            case EventTypes.PAYMENT_FAILED ->
                    log.info("Payment {} for order {} failed ({}); the order stays awaiting payment", p.paymentId(), p.orderNumber(), p.failureReason());
            default -> log.debug("Ignoring payment event type {}", event.eventType());
        }
        processed.save(ProcessedEvent.of(event.eventId()));
    }

    private void onSucceeded(PaymentEventPayload p) {
        Optional<CustomerOrder> found = orders.findById(p.orderId());
        if (found.isEmpty()) {
            log.error("Payment {} succeeded for unknown order {}. Manual refund needed.", p.paymentId(), p.orderId());
            return;
        }
        CustomerOrder order = found.get();
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.PAID || status == OrderStatus.READY_FOR_DELIVERY) {
            log.info("Order {} is already {}; payment {} changes nothing", order.getOrderNumber(), status, p.paymentId());
            return;
        }
        if (status != OrderStatus.AWAITING_PAYMENT) {
            log.error("Payment {} succeeded but order {} is {}. It cannot be paid: MANUAL REFUND NEEDED.",
                    p.paymentId(), order.getOrderNumber(), status);
            return;
        }
        order.moveTo(OrderStatus.PAID);
        history.save(OrderStatusHistory.of(order.getId(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, null, SYSTEM,
                "Payment " + p.paymentId() + " confirmed"));
        order.moveTo(OrderStatus.READY_FOR_DELIVERY);
        history.save(OrderStatusHistory.of(order.getId(), OrderStatus.PAID, OrderStatus.READY_FOR_DELIVERY, null, SYSTEM, null));
        orders.saveAndFlush(order);
        outbox.orderEvent(EventTypes.ORDER_READY_FOR_DELIVERY, order, null);
    }
}
