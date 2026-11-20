package com.orderplace.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orderplace.common.events.EventTypes;
import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.order.api.dto.ApproveRequest;
import com.orderplace.order.api.dto.EditApproveRequest;
import com.orderplace.order.api.dto.OrderLineResponse;
import com.orderplace.order.api.dto.OrderResponse;
import com.orderplace.order.api.dto.RejectRequest;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.DealerAction;
import com.orderplace.order.model.DealerActionType;
import com.orderplace.order.model.LineSpec;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.model.OrderStatusHistory;
import com.orderplace.order.outbox.OutboxWriter;
import com.orderplace.order.repository.DealerActionRepository;
import com.orderplace.order.repository.OrderRepository;
import com.orderplace.order.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DealerOrderServiceTest {

    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository history;
    @Mock DealerActionRepository actions;
    @Mock OutboxWriter outbox;
    @InjectMocks DealerOrderService service;

    private final UUID dealerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID line1 = UUID.randomUUID();
    private final UUID line2 = UUID.randomUUID();
    private final TenantContext ctx = new TenantContext(userId.toString(), "DEALER", "DEALER", dealerId);

    /** Two lines: 10 x 100.00 and 4 x 250.00 = 2000.00 */
    private CustomerOrder order(OrderStatus target) {
        CustomerOrder o = CustomerOrder.newDraft(UUID.randomUUID(), "Customer", "c@x.com", UUID.randomUUID(), "Address");
        o.saveDraft(dealerId, "Dealer", "d@x.com", "Address", null, List.of(
                new LineSpec(UUID.randomUUID(), "CHR-1", "Chair", new BigDecimal("100.00"), 10),
                new LineSpec(UUID.randomUUID(), "DSK-1", "Desk", new BigDecimal("250.00"), 4)));
        ReflectionTestUtils.setField(o.getLines().get(0), "id", line1);
        ReflectionTestUtils.setField(o.getLines().get(1), "id", line2);
        if (target != OrderStatus.DRAFT) o.submit("OP-2026-000001", "k", Instant.now());
        if (target == OrderStatus.UNDER_REVIEW) o.startReview();
        return o;
    }

    private void found(CustomerOrder o) {
        when(orders.findByIdAndDealerId(orderId, dealerId)).thenReturn(Optional.of(o));
    }

    private static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private void saving() {
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(actions.save(any(DealerAction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- visibility

    @Test
    void nonDealer_isForbidden() {
        TenantContext customer = new TenantContext(userId.toString(), "CUSTOMER", "CUSTOMER", UUID.randomUUID());

        assertThatThrownBy(() -> service.get(customer, orderId)).satisfies(t -> assertCode(t, "FORBIDDEN"));
        verifyNoInteractions(orders);
    }

    @Test
    void orderOfAnotherDealer_isNotFound() {
        when(orders.findByIdAndDealerId(orderId, dealerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ctx, orderId)).satisfies(t -> assertCode(t, "NOT_FOUND"));
    }

    @Test
    void draftOrders_areInvisibleToDealers() {
        found(order(OrderStatus.DRAFT));

        assertThatThrownBy(() -> service.get(ctx, orderId)).satisfies(t -> assertCode(t, "NOT_FOUND"));
    }

    // ---- start review

    @Test
    void startReview_movesSubmittedToUnderReviewAndRecordsHistory() {
        CustomerOrder o = order(OrderStatus.SUBMITTED);
        found(o);
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.startReview(ctx, orderId, null);

        assertThat(response.status()).isEqualTo(OrderStatus.UNDER_REVIEW);
        ArgumentCaptor<OrderStatusHistory> h = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(h.capture());
        assertThat(h.getValue().getChangedByRole()).isEqualTo("DEALER");
        assertThat(h.getValue().getFromStatus()).isEqualTo(OrderStatus.SUBMITTED);
        verifyNoInteractions(outbox);   // no event for start-review
    }

    @Test
    void startReview_twice_isAConflict() {
        found(order(OrderStatus.UNDER_REVIEW));

        assertThatThrownBy(() -> service.startReview(ctx, orderId, null))
                .satisfies(t -> assertCode(t, "INVALID_STATE_TRANSITION"));
    }

    // ---- approve

    @Test
    void approve_approvesEveryLineAtSubmittedQuantity() {
        CustomerOrder o = order(OrderStatus.UNDER_REVIEW);
        found(o);
        saving();

        OrderResponse response = service.approve(ctx, orderId, new ApproveRequest(null, " Stock confirmed "));

        assertThat(response.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(response.lines()).extracting(OrderLineResponse::approvedQty).containsExactly(10, 4);
        assertThat(response.totalAmount()).isEqualByComparingTo("2000.00");
        assertThat(response.dealerAction().action()).isEqualTo("APPROVE");
        assertThat(response.dealerAction().remarks()).isEqualTo("Stock confirmed");
        verify(outbox).orderEvent(EventTypes.ORDER_APPROVED, o, "Stock confirmed");
    }

    @Test
    void approve_beforeReviewStarted_isAConflict() {
        found(order(OrderStatus.SUBMITTED));

        assertThatThrownBy(() -> service.approve(ctx, orderId, new ApproveRequest(null, null)))
                .satisfies(t -> assertCode(t, "INVALID_STATE_TRANSITION"));
        verifyNoInteractions(outbox, history, actions);
    }

    @Test
    void approve_withStaleVersion_saysOrderAlreadyProcessed() {
        CustomerOrder o = order(OrderStatus.UNDER_REVIEW);
        ReflectionTestUtils.setField(o, "version", 5L);
        found(o);

        assertThatThrownBy(() -> service.approve(ctx, orderId, new ApproveRequest(4L, null)))
                .satisfies(t -> assertCode(t, "VERSION_CONFLICT"));
        verify(orders, never()).saveAndFlush(any());
    }

    // ---- reject

    @Test
    void reject_recordsReasonAndWritesEvent() {
        CustomerOrder o = order(OrderStatus.UNDER_REVIEW);
        found(o);
        saving();

        OrderResponse response = service.reject(ctx, orderId, new RejectRequest(null, " Item discontinued "));

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(response.dealerAction().action()).isEqualTo("REJECT");
        verify(outbox).orderEvent(eq(EventTypes.ORDER_REJECTED), eq(o), eq("Item discontinued"));
        ArgumentCaptor<DealerAction> a = ArgumentCaptor.forClass(DealerAction.class);
        verify(actions).save(a.capture());
        assertThat(a.getValue().getAction()).isEqualTo(DealerActionType.REJECT);
        assertThat(a.getValue().getDealerUserId()).isEqualTo(userId);
    }

    // ---- edit and approve

    @Test
    void editAndApprove_reducesQuantitiesAndRecalculatesTotal() {
        CustomerOrder o = order(OrderStatus.UNDER_REVIEW);
        found(o);
        saving();

        OrderResponse response = service.editAndApprove(ctx, orderId, new EditApproveRequest(null, "Only 8 chairs",
                List.of(new EditApproveRequest.Line(line1, 8), new EditApproveRequest.Line(line2, 0))));

        assertThat(response.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(response.lines()).extracting(OrderLineResponse::submittedQty).containsExactly(10, 4);   // never changed
        assertThat(response.lines()).extracting(OrderLineResponse::approvedQty).containsExactly(8, 0);
        assertThat(response.totalAmount()).isEqualByComparingTo("800.00");
        assertThat(response.dealerAction().action()).isEqualTo("EDIT");
        verify(outbox).orderEvent(EventTypes.ORDER_EDITED, o, "Only 8 chairs");
    }

    @Test
    void editAndApprove_linesNotListedKeepTheirQuantity() {
        CustomerOrder o = order(OrderStatus.UNDER_REVIEW);
        found(o);
        saving();

        OrderResponse response = service.editAndApprove(ctx, orderId, new EditApproveRequest(null, "Reduced chairs",
                List.of(new EditApproveRequest.Line(line1, 5))));

        assertThat(response.lines()).extracting(OrderLineResponse::approvedQty).containsExactly(5, 4);
        assertThat(response.totalAmount()).isEqualByComparingTo("1500.00");   // 5*100 + 4*250
    }

    @Test
    void editAndApprove_cannotIncreaseQuantity() {
        found(order(OrderStatus.UNDER_REVIEW));

        assertThatThrownBy(() -> service.editAndApprove(ctx, orderId, new EditApproveRequest(null, "More",
                List.of(new EditApproveRequest.Line(line1, 11)))))
                .satisfies(t -> assertCode(t, "QTY_EXCEEDS_SUBMITTED"));
        verifyNoInteractions(outbox);
    }

    @Test
    void editAndApprove_unknownLine_isRejected() {
        found(order(OrderStatus.UNDER_REVIEW));

        assertThatThrownBy(() -> service.editAndApprove(ctx, orderId, new EditApproveRequest(null, "x",
                List.of(new EditApproveRequest.Line(UUID.randomUUID(), 1)))))
                .satisfies(t -> assertCode(t, "VALIDATION_ERROR"));
    }

    @Test
    void editAndApprove_sameLineTwice_isRejected() {
        found(order(OrderStatus.UNDER_REVIEW));

        assertThatThrownBy(() -> service.editAndApprove(ctx, orderId, new EditApproveRequest(null, "x",
                List.of(new EditApproveRequest.Line(line1, 5), new EditApproveRequest.Line(line1, 6)))))
                .satisfies(t -> assertCode(t, "VALIDATION_ERROR"));
    }

    @Test
    void editAndApprove_withNoChange_isRejected() {
        found(order(OrderStatus.UNDER_REVIEW));

        assertThatThrownBy(() -> service.editAndApprove(ctx, orderId, new EditApproveRequest(null, "x",
                List.of(new EditApproveRequest.Line(line1, 10)))))
                .satisfies(t -> assertCode(t, "NO_CHANGES"));
    }

    @Test
    void editAndApprove_removingEverything_isRejected() {
        found(order(OrderStatus.UNDER_REVIEW));

        assertThatThrownBy(() -> service.editAndApprove(ctx, orderId, new EditApproveRequest(null, "x",
                List.of(new EditApproveRequest.Line(line1, 0), new EditApproveRequest.Line(line2, 0)))))
                .satisfies(t -> assertCode(t, "NO_LINES_APPROVED"));
    }

    @Test
    void editAndApprove_inWrongState_isAConflict() {
        found(order(OrderStatus.SUBMITTED));

        assertThatThrownBy(() -> service.editAndApprove(ctx, orderId, new EditApproveRequest(null, "x",
                List.of(new EditApproveRequest.Line(line1, 5)))))
                .satisfies(t -> assertCode(t, "INVALID_STATE_TRANSITION"));
    }
}
