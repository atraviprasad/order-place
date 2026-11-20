package com.orderplace.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** One attempt to pay an order. Holds gateway references only, never card data. */
@Entity
@Table(name = "payment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    public static final String GATEWAY = "RAZORPAY";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, updatable = false, length = 30)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "payer_user_id", nullable = false, updatable = false)
    private UUID payerUserId;

    @Column(name = "payer_email", length = 254)
    private String payerEmail;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "gateway_name", nullable = false, updatable = false, length = 30)
    private String gatewayName = GATEWAY;

    @Column(name = "gateway_order_ref", nullable = false, updatable = false, length = 60)
    private String gatewayOrderRef;

    @Column(name = "gateway_payment_ref", length = 60)
    private String gatewayPaymentRef;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Payment initiate(UUID orderId, String orderNumber, UUID customerId, UUID payerUserId, String payerEmail,
                                   BigDecimal amount, String currency, String gatewayOrderRef, String idempotencyKey) {
        Payment p = new Payment();
        p.orderId = orderId;
        p.orderNumber = orderNumber;
        p.customerId = customerId;
        p.payerUserId = payerUserId;
        p.payerEmail = payerEmail;
        p.amount = amount;
        p.currency = currency;
        p.gatewayOrderRef = gatewayOrderRef;
        p.idempotencyKey = idempotencyKey;
        p.status = PaymentStatus.INITIATED;
        p.initiatedAt = Instant.now();
        return p;
    }

    /** The browser reported a valid checkout. Only an INITIATED payment moves; the webhook has the final word. */
    public boolean markPendingConfirmation(String gatewayPaymentRef) {
        if (status != PaymentStatus.INITIATED) return false;
        this.status = PaymentStatus.PENDING_CONFIRMATION;
        this.gatewayPaymentRef = gatewayPaymentRef;
        return true;
    }

    /** SUCCESS is final. A payment that was reported FAILED can still succeed (the customer retried inside Checkout). */
    public boolean markSuccess(String gatewayPaymentRef) {
        if (status == PaymentStatus.SUCCESS) return false;
        this.status = PaymentStatus.SUCCESS;
        if (gatewayPaymentRef != null) this.gatewayPaymentRef = gatewayPaymentRef;
        this.failureReason = null;
        this.completedAt = Instant.now();
        return true;
    }

    /** A failure never overrides a success or repeats a failure. */
    public boolean markFailed(String reason, String gatewayPaymentRef) {
        if (status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED) return false;
        this.status = PaymentStatus.FAILED;
        if (gatewayPaymentRef != null) this.gatewayPaymentRef = gatewayPaymentRef;
        this.failureReason = reason;
        this.completedAt = Instant.now();
        return true;
    }
}
