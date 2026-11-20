package com.orderplace.payment.api.dto;

import com.orderplace.payment.model.Payment;
import com.orderplace.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** gatewayOrderId and gatewayKeyId are what the web app passes to Razorpay Checkout (the key id is the public test key). */
public record PaymentResponse(UUID paymentId, UUID orderId, String orderNumber, BigDecimal amount, String currency,
                              PaymentStatus status, String gateway, String gatewayOrderId, String gatewayKeyId,
                              String failureReason, Instant initiatedAt, Instant completedAt, Long version) {

    public static PaymentResponse of(Payment p, String gatewayKeyId) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getOrderNumber(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getGatewayName(), p.getGatewayOrderRef(), gatewayKeyId, p.getFailureReason(),
                p.getInitiatedAt(), p.getCompletedAt(), p.getVersion());
    }
}
