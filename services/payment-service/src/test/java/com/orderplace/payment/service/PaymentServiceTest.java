package com.orderplace.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.payment.api.dto.ClientCallbackRequest;
import com.orderplace.payment.client.OrderClient;
import com.orderplace.payment.client.OrderInfo;
import com.orderplace.payment.model.Payment;
import com.orderplace.payment.model.PaymentStatus;
import com.orderplace.payment.gateway.GatewayOrder;
import com.orderplace.payment.gateway.PaymentGatewayClient;
import com.orderplace.payment.gateway.SignatureVerifier;
import com.orderplace.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository payments;
    @Mock OrderClient orders;
    @Mock PaymentGatewayClient gateway;

    private final SignatureVerifier signatures = new SignatureVerifier("key-secret", "webhook-secret");
    private PaymentService service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final TenantContext customer = new TenantContext(UUID.randomUUID().toString(), "CUSTOMER", "CUSTOMER", customerId);
    private final TenantContext admin = new TenantContext(UUID.randomUUID().toString(), "ADMIN", null, null);
    private final TenantContext dealer = new TenantContext(UUID.randomUUID().toString(), "DEALER", "DEALER", UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, orders, gateway, signatures);
    }

    private OrderInfo order(String status) {
        return new OrderInfo(orderId, "ORD-1", status, customerId, new BigDecimal("1200.50"), "INR");
    }

    private Payment payment(String gatewayOrder, String key) {
        return Payment.initiate(orderId, "ORD-1", customerId, UUID.randomUUID(), "a@b.com",
                new BigDecimal("1200.50"), "INR", gatewayOrder, key);
    }

    @Test
    void startCreatesGatewayOrderAndPayment() {
        when(payments.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(orders.get(orderId)).thenReturn(order("AWAITING_PAYMENT"));
        when(payments.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(false);
        when(payments.findFirstByOrderIdAndStatusIn(orderId, PaymentStatus.OPEN)).thenReturn(Optional.empty());
        when(gateway.createOrder(eq(new BigDecimal("1200.50")), eq("INR"), eq("ORD-1"), anyMap())).thenReturn(new GatewayOrder("order_G1"));
        when(gateway.keyId()).thenReturn("rzp_test_x");
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentService.Result r = service.start(customer, "a@b.com", orderId, "k1");

        assertThat(r.created()).isTrue();
        assertThat(r.payment().status()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(r.payment().gatewayOrderId()).isEqualTo("order_G1");
        assertThat(r.payment().gatewayKeyId()).isEqualTo("rzp_test_x");
        assertThat(r.payment().amount()).isEqualByComparingTo("1200.50");
    }

    @Test
    void startWithSameKeyReturnsSamePaymentWithoutCallingGateway() {
        when(payments.findByIdempotencyKey("k1")).thenReturn(Optional.of(payment("order_G1", "k1")));
        when(gateway.keyId()).thenReturn("rzp_test_x");

        PaymentService.Result r = service.start(customer, "a@b.com", orderId, "k1");

        assertThat(r.created()).isFalse();
        verify(gateway, never()).createOrder(any(), any(), any(), any());
        verify(orders, never()).get(any());
    }

    @Test
    void startWithKeyUsedForOtherOrderIsConflict() {
        when(payments.findByIdempotencyKey("k1")).thenReturn(Optional.of(payment("order_G1", "k1")));

        assertThatThrownBy(() -> service.start(customer, "a@b.com", UUID.randomUUID(), "k1"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Idempotency-Key");
    }

    @Test
    void startRejectedWhenOrderNotAwaitingPayment() {
        when(payments.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(orders.get(orderId)).thenReturn(order("UNDER_REVIEW"));

        assertThatThrownBy(() -> service.start(customer, "a@b.com", orderId, "k1"))
                .isInstanceOf(ApiException.class).hasMessageContaining("UNDER_REVIEW");
        verify(gateway, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    void startRejectedWhenOrderAlreadyPaid() {
        when(payments.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(orders.get(orderId)).thenReturn(order("AWAITING_PAYMENT"));
        when(payments.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(true);

        assertThatThrownBy(() -> service.start(customer, "a@b.com", orderId, "k1"))
                .isInstanceOf(ApiException.class).hasMessageContaining("already been paid");
    }

    @Test
    void startReturnsOpenPaymentInsteadOfCreatingSecondOne() {
        when(payments.findByIdempotencyKey("k2")).thenReturn(Optional.empty());
        when(orders.get(orderId)).thenReturn(order("AWAITING_PAYMENT"));
        when(payments.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(false);
        when(payments.findFirstByOrderIdAndStatusIn(orderId, PaymentStatus.OPEN)).thenReturn(Optional.of(payment("order_G1", "k1")));
        when(gateway.keyId()).thenReturn("rzp_test_x");

        PaymentService.Result r = service.start(customer, "a@b.com", orderId, "k2");

        assertThat(r.created()).isFalse();
        assertThat(r.payment().gatewayOrderId()).isEqualTo("order_G1");
        verify(gateway, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    void startRecoversFromRaceOnUniqueIndex() {
        Payment winner = payment("order_G0", "k0");
        when(payments.findByIdempotencyKey("k3")).thenReturn(Optional.empty(), Optional.empty());
        when(orders.get(orderId)).thenReturn(order("AWAITING_PAYMENT"));
        when(payments.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(false);
        when(payments.findFirstByOrderIdAndStatusIn(orderId, PaymentStatus.OPEN)).thenReturn(Optional.empty(), Optional.of(winner));
        when(gateway.createOrder(any(), any(), any(), anyMap())).thenReturn(new GatewayOrder("order_G3"));
        when(gateway.keyId()).thenReturn("rzp_test_x");
        when(payments.saveAndFlush(any(Payment.class))).thenThrow(new DataIntegrityViolationException("uq_payment_one_open_per_order"));

        PaymentService.Result r = service.start(customer, "a@b.com", orderId, "k3");

        assertThat(r.created()).isFalse();
        assertThat(r.payment().gatewayOrderId()).isEqualTo("order_G0");
    }

    @Test
    void onlyCustomersCanStartPayments() {
        assertThatThrownBy(() -> service.start(dealer, "d@b.com", orderId, "k"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.start(admin, "x@b.com", orderId, "k"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void clientCallbackWithValidSignatureMovesToPendingConfirmationOnly() {
        Payment p = payment("order_G1", "k1");
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.of(p));
        String sig = SignatureVerifierTestSupport.checkoutSig("key-secret", "order_G1", "pay_9");

        var res = service.clientCallback(customer, id, new ClientCallbackRequest("pay_9", "order_G1", sig));

        assertThat(res.status()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
        assertThat(p.getStatus()).isNotEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void clientCallbackWithBadSignatureIsRejectedAndNothingChanges() {
        Payment p = payment("order_G1", "k1");
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.clientCallback(customer, id, new ClientCallbackRequest("pay_9", "order_G1", "bad")))
                .isInstanceOf(ApiException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void clientCallbackWithOtherGatewayOrderIsRejected() {
        Payment p = payment("order_G1", "k1");
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.of(p));
        String sig = SignatureVerifierTestSupport.checkoutSig("key-secret", "order_OTHER", "pay_9");

        assertThatThrownBy(() -> service.clientCallback(customer, id, new ClientCallbackRequest("pay_9", "order_OTHER", sig)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void otherCustomersPaymentLooksNotFound() {
        Payment p = payment("order_G1", "k1");
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.of(p));
        TenantContext other = new TenantContext(UUID.randomUUID().toString(), "CUSTOMER", "CUSTOMER", UUID.randomUUID());

        assertThatThrownBy(() -> service.get(other, id)).isInstanceOf(ApiException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.get(dealer, id)).isInstanceOf(ApiException.class);
    }

    @Test
    void adminCanReadAnyPayment() {
        Payment p = payment("order_G1", "k1");
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.of(p));
        when(gateway.keyId()).thenReturn("rzp_test_x");

        assertThat(service.get(admin, id).orderId()).isEqualTo(orderId);
    }

    @Test
    void listForOrderHidesOtherCustomersPayments() {
        when(payments.findByOrderIdOrderByInitiatedAtDesc(orderId)).thenReturn(List.of(payment("order_G1", "k1")));
        TenantContext other = new TenantContext(UUID.randomUUID().toString(), "CUSTOMER", "CUSTOMER", UUID.randomUUID());

        assertThat(service.listForOrder(other, orderId)).isEmpty();
    }
}
