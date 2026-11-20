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
import com.orderplace.order.api.dto.CancelRequest;
import com.orderplace.order.api.dto.CreateOrderRequest;
import com.orderplace.order.api.dto.OrderLineRequest;
import com.orderplace.order.api.dto.OrderResponse;
import com.orderplace.order.api.dto.SaveOrderRequest;
import com.orderplace.order.client.CatalogClient;
import com.orderplace.order.client.CustomerClient;
import com.orderplace.order.client.CustomerInfo;
import com.orderplace.order.client.DealerClient;
import com.orderplace.order.client.DealerInfo;
import com.orderplace.order.client.ProductInfo;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.LineSpec;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.model.OrderStatusHistory;
import com.orderplace.order.outbox.OutboxWriter;
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

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository history;
    @Mock CatalogClient catalog;
    @Mock DealerClient dealers;
    @Mock CustomerClient customers;
    @Mock OutboxWriter outbox;
    @Mock com.orderplace.order.repository.DealerActionRepository actions;
    @InjectMocks OrderService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID dealerId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final TenantContext ctx = new TenantContext(userId.toString(), "CUSTOMER", "CUSTOMER", orgId);

    private ProductInfo product(String price, boolean available, int min, boolean active) {
        return new ProductInfo(productId, "CHR-1", "Chair", new BigDecimal(price), available, min, active);
    }

    private CustomerOrder draftWithLine(int qty) {
        CustomerOrder o = CustomerOrder.newDraft(orgId, "Customer", "c@x.com", userId, "Address");
        o.saveDraft(dealerId, "Dealer", "d@x.com", "Address", null,
                List.of(new LineSpec(productId, "CHR-1", "Chair", new BigDecimal("100.00"), qty)));
        return o;
    }

    private CustomerOrder submittedOrder(String key) {
        CustomerOrder o = draftWithLine(10);
        o.submit("OP-2026-000001", key, Instant.now());
        return o;
    }

    private static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    // ---- create / update / delete

    @Test
    void createDraft_usesCustomerAddressAndRecordsHistory() {
        when(customers.me()).thenReturn(new CustomerInfo(orgId, "Demo Customer", "c@x.com", "Customer HQ address", true));
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.createDraft(ctx, new CreateOrderRequest(null, null, null, null));

        assertThat(response.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(response.deliveryAddress()).isEqualTo("Customer HQ address");
        assertThat(response.customerName()).isEqualTo("Demo Customer");
        assertThat(response.orderNumber()).isNull();
        ArgumentCaptor<OrderStatusHistory> h = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(h.capture());
        assertThat(h.getValue().getFromStatus()).isNull();
        assertThat(h.getValue().getToStatus()).isEqualTo(OrderStatus.DRAFT);
    }

    @Test
    void createDraft_withDealerAndLines_snapshotsPricesAndDealer() {
        when(customers.me()).thenReturn(new CustomerInfo(orgId, "Demo Customer", "c@x.com", "HQ", true));
        when(dealers.requireActive(dealerId)).thenReturn(new DealerInfo(dealerId, "Dealer One", "d@x.com"));
        when(catalog.get(productId)).thenReturn(product("4500.00", true, 1, true));
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.createDraft(ctx, new CreateOrderRequest(dealerId, "Site B", "Please hurry",
                List.of(new OrderLineRequest(productId, 10))));

        assertThat(response.deliveryAddress()).isEqualTo("Site B");
        assertThat(response.dealer().name()).isEqualTo("Dealer One");
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).unitPrice()).isEqualByComparingTo("4500.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("45000.00");
    }

    @Test
    void createDraft_forInactiveOrganisation_isRejected() {
        when(customers.me()).thenReturn(new CustomerInfo(orgId, "Demo", "c@x.com", "HQ", false));

        assertThatThrownBy(() -> service.createDraft(ctx, new CreateOrderRequest(null, null, null, null)))
                .satisfies(t -> assertCode(t, "ORGANISATION_INACTIVE"));
    }

    @Test
    void createDraft_sameProductTwice_isRejected() {
        when(customers.me()).thenReturn(new CustomerInfo(orgId, "Demo", "c@x.com", "HQ", true));
        when(catalog.get(productId)).thenReturn(product("100.00", true, 1, true));

        assertThatThrownBy(() -> service.createDraft(ctx, new CreateOrderRequest(null, null, null,
                List.of(new OrderLineRequest(productId, 1), new OrderLineRequest(productId, 2)))))
                .satisfies(t -> assertCode(t, "VALIDATION_ERROR"));
    }

    @Test
    void createDraft_withInactiveProduct_isRejected() {
        when(customers.me()).thenReturn(new CustomerInfo(orgId, "Demo", "c@x.com", "HQ", true));
        when(catalog.get(productId)).thenReturn(product("100.00", true, 1, false));

        assertThatThrownBy(() -> service.createDraft(ctx, new CreateOrderRequest(null, null, null,
                List.of(new OrderLineRequest(productId, 1))))).satisfies(t -> assertCode(t, "PRODUCT_UNAVAILABLE"));
    }

    @Test
    void anyone_whoIsNotACustomerUser_isForbidden() {
        TenantContext admin = new TenantContext(userId.toString(), "ADMIN", null, null);

        assertThatThrownBy(() -> service.createDraft(admin, new CreateOrderRequest(null, null, null, null)))
                .satisfies(t -> assertCode(t, "FORBIDDEN"));
        assertThatThrownBy(() -> service.get(admin, orderId)).satisfies(t -> assertCode(t, "FORBIDDEN"));
        verifyNoInteractions(orders);
    }

    @Test
    void get_orderOfAnotherCustomer_isNotFound() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ctx, orderId)).satisfies(t -> assertCode(t, "NOT_FOUND"));
    }

    @Test
    void updateDraft_afterSubmit_isRejected() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(submittedOrder("k")));

        assertThatThrownBy(() -> service.updateDraft(ctx, orderId, new SaveOrderRequest(null, null, "x", null, null)))
                .satisfies(t -> assertCode(t, "ORDER_NOT_EDITABLE"));
    }

    @Test
    void updateDraft_staleVersion_isRejected() {
        CustomerOrder o = draftWithLine(1);
        org.springframework.test.util.ReflectionTestUtils.setField(o, "version", 7L);
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.updateDraft(ctx, orderId, new SaveOrderRequest(6L, null, "x", null, null)))
                .satisfies(t -> assertCode(t, "VERSION_CONFLICT"));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void deleteDraft_works_butNotAfterSubmit() {
        CustomerOrder draft = draftWithLine(1);
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(draft));
        service.deleteDraft(ctx, orderId);
        verify(orders).delete(draft);

        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(submittedOrder("k")));
        assertThatThrownBy(() -> service.deleteDraft(ctx, orderId)).satisfies(t -> assertCode(t, "ORDER_NOT_EDITABLE"));
    }

    // ---- submit

    @Test
    void submit_refreshesPricesNumbersTheOrderAndWritesTheEvent() {
        CustomerOrder o = draftWithLine(10);
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(o));
        when(dealers.requireActive(dealerId)).thenReturn(new DealerInfo(dealerId, "Dealer Renamed", "new@x.com"));
        when(catalog.get(productId)).thenReturn(product("120.00", true, 5, true));
        when(orders.nextOrderSequence()).thenReturn(123L);
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.submit(ctx, orderId, "key-1", null);

        assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(response.orderNumber()).matches("OP-\\d{4}-000123");
        assertThat(response.lines().get(0).unitPrice()).isEqualByComparingTo("120.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("1200.00");
        assertThat(response.dealer().name()).isEqualTo("Dealer Renamed");
        assertThat(o.getDealerEmail()).isEqualTo("new@x.com");
        verify(outbox).orderEvent(EventTypes.ORDER_SUBMITTED, o, null);
        ArgumentCaptor<OrderStatusHistory> h = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(h.capture());
        assertThat(h.getValue().getFromStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(h.getValue().getToStatus()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(h.getValue().getChangedByRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void submit_withoutDealer_isRejected() {
        CustomerOrder o = CustomerOrder.newDraft(orgId, "Customer", "c@x.com", userId, "Address");
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.submit(ctx, orderId, "k", null)).satisfies(t -> assertCode(t, "VALIDATION_ERROR"));
    }

    @Test
    void submit_withoutLines_isRejected() {
        CustomerOrder o = CustomerOrder.newDraft(orgId, "Customer", "c@x.com", userId, "Address");
        o.saveDraft(dealerId, "Dealer", null, "Address", null, List.of());
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.submit(ctx, orderId, "k", null)).satisfies(t -> assertCode(t, "VALIDATION_ERROR"));
    }

    @Test
    void submit_withInactiveDealer_isRejected() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(draftWithLine(1)));
        when(dealers.requireActive(dealerId)).thenThrow(ApiException.unprocessable("DEALER_INACTIVE", "inactive"));

        assertThatThrownBy(() -> service.submit(ctx, orderId, "k", null)).satisfies(t -> assertCode(t, "DEALER_INACTIVE"));
    }

    @Test
    void submit_outOfStockProduct_isRejectedWithoutSideEffects() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(draftWithLine(10)));
        when(dealers.requireActive(dealerId)).thenReturn(new DealerInfo(dealerId, "Dealer", null));
        when(catalog.get(productId)).thenReturn(product("100.00", false, 1, true));

        assertThatThrownBy(() -> service.submit(ctx, orderId, "k", null)).satisfies(t -> assertCode(t, "PRODUCT_UNAVAILABLE"));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(outbox, history);
    }

    @Test
    void submit_belowMinimumQuantity_isRejected() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(draftWithLine(3)));
        when(dealers.requireActive(dealerId)).thenReturn(new DealerInfo(dealerId, "Dealer", null));
        when(catalog.get(productId)).thenReturn(product("100.00", true, 10, true));

        assertThatThrownBy(() -> service.submit(ctx, orderId, "k", null)).satisfies(t -> assertCode(t, "BELOW_MIN_QTY"));
    }

    @Test
    void submit_sameIdempotencyKeyAgain_returnsTheOrderWithoutRepeatingAnything() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(submittedOrder("key-1")));

        OrderResponse response = service.submit(ctx, orderId, "key-1", null);

        assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED);
        verifyNoInteractions(outbox, history, dealers, catalog);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void submit_differentKeyOnSubmittedOrder_isAConflict() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(submittedOrder("key-1")));

        assertThatThrownBy(() -> service.submit(ctx, orderId, "other-key", null))
                .satisfies(t -> assertCode(t, "INVALID_STATE_TRANSITION"));
    }

    // ---- cancel

    @Test
    void cancel_submittedOrder_recordsReasonAndWritesEvent() {
        CustomerOrder o = submittedOrder("k");
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(o));
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.cancel(ctx, orderId, new CancelRequest(null, " Budget cut "));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(outbox).orderEvent(eq(EventTypes.ORDER_CANCELLED), eq(o), eq("Budget cut"));
        ArgumentCaptor<OrderStatusHistory> h = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(h.capture());
        assertThat(h.getValue().getFromStatus()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(h.getValue().getReason()).isEqualTo("Budget cut");
    }

    @Test
    void cancel_withoutBody_works() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(submittedOrder("k")));
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.cancel(ctx, orderId, null).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_draft_isRejected() {
        when(orders.findByIdAndCustomerId(orderId, orgId)).thenReturn(Optional.of(draftWithLine(1)));

        assertThatThrownBy(() -> service.cancel(ctx, orderId, null)).satisfies(t -> assertCode(t, "INVALID_STATE_TRANSITION"));
        verifyNoInteractions(outbox);
    }
}
