package com.orderplace.payment.service;

import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.payment.api.dto.CallbackResponse;
import com.orderplace.payment.api.dto.ClientCallbackRequest;
import com.orderplace.payment.api.dto.PaymentResponse;
import com.orderplace.payment.client.OrderClient;
import com.orderplace.payment.client.OrderInfo;
import com.orderplace.payment.model.Payment;
import com.orderplace.payment.model.PaymentStatus;
import com.orderplace.payment.gateway.GatewayOrder;
import com.orderplace.payment.gateway.PaymentGatewayClient;
import com.orderplace.payment.gateway.SignatureVerifier;
import com.orderplace.payment.repository.PaymentRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-side payment use cases. The customer is always the organisation in the token. The amount is always the
 * order's total as the Order Service reports it. The browser can never mark a payment as paid: only the signed webhook can.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String AWAITING_PAYMENT = "AWAITING_PAYMENT";

    /** created = true means a new payment (HTTP 201), false means an existing one was returned (HTTP 200). */
    public record Result(PaymentResponse payment, boolean created) {}

    private final PaymentRepository payments;
    private final OrderClient orders;
    private final PaymentGatewayClient gateway;
    private final SignatureVerifier signatures;

    public PaymentService(PaymentRepository payments, OrderClient orders, PaymentGatewayClient gateway,
                          SignatureVerifier signatures) {
        this.payments = payments;
        this.orders = orders;
        this.gateway = gateway;
        this.signatures = signatures;
    }

    @Transactional
    public Result start(TenantContext ctx, String payerEmail, UUID orderId, String idempotencyKey) {
        requireCustomer(ctx);

        Optional<Payment> sameKey = payments.findByIdempotencyKey(idempotencyKey);
        if (sameKey.isPresent()) {
            Payment p = sameKey.get();
            if (!p.getOrderId().equals(orderId) || !p.getCustomerId().equals(ctx.orgId())) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different request");
            }
            return new Result(view(p), false);
        }

        OrderInfo order = orders.get(orderId);   // 404 when the order is not this customer's
        if (!AWAITING_PAYMENT.equals(order.status())) {
            throw ApiException.unprocessable("PAYMENT_NOT_ALLOWED", "The order is " + order.status() + ", not awaiting payment");
        }
        if (payments.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)) {
            throw ApiException.unprocessable("PAYMENT_NOT_ALLOWED", "This order has already been paid");
        }
        Optional<Payment> open = payments.findFirstByOrderIdAndStatusIn(orderId, PaymentStatus.OPEN);
        if (open.isPresent()) {
            return new Result(view(open.get()), false);   // a payment is already in progress: continue with it
        }

        GatewayOrder gatewayOrder = gateway.createOrder(order.totalAmount(), order.currency(), order.orderNumber(),
                Map.of("orderId", orderId.toString(), "orderNumber", String.valueOf(order.orderNumber())));
        Payment payment = Payment.initiate(orderId, order.orderNumber(), ctx.orgId(), UUID.fromString(ctx.userId()),
                payerEmail, order.totalAmount(), order.currency(), gatewayOrder.id(), idempotencyKey);
        try {
            return new Result(view(payments.saveAndFlush(payment)), true);
        } catch (DataIntegrityViolationException e) {
            // two requests raced: the database allowed only one open payment per order, so use the winner
            log.info("Concurrent payment start for order {}, returning the existing one", orderId);
            Payment existing = payments.findByIdempotencyKey(idempotencyKey)
                    .or(() -> payments.findFirstByOrderIdAndStatusIn(orderId, PaymentStatus.OPEN))
                    .orElseThrow(() -> e);
            return new Result(view(existing), false);
        }
    }

    /**
     * The browser reports the Checkout result. A valid signature moves INITIATED to PENDING_CONFIRMATION and nothing
     * more: the order becomes paid only when the webhook confirms it.
     */
    @Transactional
    public CallbackResponse clientCallback(TenantContext ctx, UUID paymentId, ClientCallbackRequest r) {
        Payment payment = find(ctx, paymentId);
        if (!payment.getGatewayOrderRef().equals(r.razorpayOrderId())
                || !signatures.verifyCheckout(r.razorpayOrderId(), r.razorpayPaymentId(), r.razorpaySignature())) {
            throw ApiException.badRequest("The payment signature is not valid");
        }
        if (payment.markPendingConfirmation(r.razorpayPaymentId())) {
            payments.saveAndFlush(payment);
        }
        return new CallbackResponse(payment.getStatus());
    }

    public PaymentResponse get(TenantContext ctx, UUID paymentId) {
        return view(find(ctx, paymentId));
    }

    public List<PaymentResponse> listForOrder(TenantContext ctx, UUID orderId) {
        return payments.findByOrderIdOrderByInitiatedAtDesc(orderId).stream()
                .filter(p -> ctx.isAdmin() || (ctx.orgId() != null && ctx.orgId().equals(p.getCustomerId())))
                .map(this::view).toList();
    }

    private Payment find(TenantContext ctx, UUID id) {
        Payment p = payments.findById(id).orElseThrow(() -> ApiException.notFound("Payment not found: " + id));
        boolean allowed = ctx.isAdmin() || (ctx.isCustomer() && ctx.orgId() != null && ctx.orgId().equals(p.getCustomerId()));
        if (!allowed) throw ApiException.notFound("Payment not found: " + id);   // never reveal other customers' payments
        return p;
    }

    private PaymentResponse view(Payment p) {
        return PaymentResponse.of(p, gateway.keyId());
    }

    private static void requireCustomer(TenantContext ctx) {
        if (!ctx.isCustomer() || ctx.orgId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only users of a customer organisation can pay");
        }
    }
}
