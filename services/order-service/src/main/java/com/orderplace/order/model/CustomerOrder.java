package com.orderplace.order.model;

import com.orderplace.common.web.ApiException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A customer's order. (Named CustomerOrder because ORDER is a reserved word in SQL and JPQL.)
 * Every state change goes through a method here, so the rules cannot be bypassed by a service.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", length = 30)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "customer_name_snapshot", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_email_snapshot", length = 254)
    private String customerEmail;

    @Column(name = "dealer_id")
    private UUID dealerId;

    @Column(name = "dealer_name_snapshot", length = 200)
    private String dealerName;

    @Column(name = "dealer_email_snapshot", length = 254)
    private String dealerEmail;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "delivery_address", columnDefinition = "text")
    private String deliveryAddress;

    @Column(name = "customer_notes", columnDefinition = "text")
    private String customerNotes;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency = "INR";

    @Column(name = "submit_idempotency_key", length = 100)
    private String submitIdempotencyKey;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<OrderLine> lines = new ArrayList<>();

    public static CustomerOrder newDraft(UUID customerId, String customerName, String customerEmail,
                                         UUID createdByUserId, String deliveryAddress) {
        CustomerOrder o = new CustomerOrder();
        o.status = OrderStatus.DRAFT;
        o.customerId = customerId;
        o.customerName = customerName;
        o.customerEmail = customerEmail;
        o.createdByUserId = createdByUserId;
        o.deliveryAddress = deliveryAddress;
        return o;
    }

    /** Replaces the editable parts of a draft. Lines are matched by product so existing rows are updated in place. */
    public void saveDraft(UUID dealerId, String dealerName, String dealerEmail, String deliveryAddress,
                          String customerNotes, List<LineSpec> specs) {
        requireStatus(OrderStatus.DRAFT, "ORDER_NOT_EDITABLE", "Only a draft order can be edited");
        this.dealerId = dealerId;
        this.dealerName = dealerName;
        this.dealerEmail = dealerEmail;
        this.deliveryAddress = deliveryAddress;
        this.customerNotes = customerNotes;
        syncLines(specs);
    }

    public void updateDealerSnapshot(String name, String email) {
        this.dealerName = name;
        this.dealerEmail = email;
    }

    /**
     * Makes the lines match the given specs (same products get updated, missing ones are removed, new ones added).
     * Updating in place avoids a delete-then-insert clash on the (order, product) unique index.
     */
    public void syncLines(List<LineSpec> specs) {
        Map<UUID, OrderLine> existing = new HashMap<>();
        for (OrderLine l : lines) existing.put(l.getProductId(), l);

        List<UUID> keep = specs.stream().map(LineSpec::productId).toList();
        for (Iterator<OrderLine> it = lines.iterator(); it.hasNext(); ) {
            if (!keep.contains(it.next().getProductId())) it.remove();
        }
        int lineNo = 1;
        for (LineSpec spec : specs) {
            OrderLine line = existing.get(spec.productId());
            if (line == null) {
                lines.add(OrderLine.create(this, lineNo, spec));
            } else {
                line.apply(lineNo, spec);
            }
            lineNo++;
        }
        recalculate();
    }

    /** DRAFT to SUBMITTED. The prices in the lines are frozen from here on. */
    public void submit(String orderNumber, String idempotencyKey, Instant now) {
        moveTo(OrderStatus.SUBMITTED);
        this.orderNumber = orderNumber;
        this.submitIdempotencyKey = idempotencyKey;
        this.submittedAt = now;
    }

    /** SUBMITTED to UNDER_REVIEW. The dealer has opened the order. */
    public void startReview() {
        moveTo(OrderStatus.UNDER_REVIEW);
    }

    /**
     * UNDER_REVIEW to AWAITING_PAYMENT. Every line gets an approved quantity: the given one, or the submitted
     * quantity when the line is not mentioned. Submitted quantities and prices are never touched.
     */
    public void approve(Map<UUID, Integer> approvedByLineId) {
        moveTo(OrderStatus.AWAITING_PAYMENT);
        for (OrderLine l : lines) {
            l.approve(approvedByLineId.getOrDefault(l.getId(), l.getSubmittedQty()));
        }
        recalculate();
    }

    /** UNDER_REVIEW to REJECTED. */
    public void reject() {
        moveTo(OrderStatus.REJECTED);
    }

    /** Customer cancel: allowed from SUBMITTED, UNDER_REVIEW and AWAITING_PAYMENT only. */
    public void cancel() {
        moveTo(OrderStatus.CANCELLED);
    }

    public void moveTo(OrderStatus next) {
        if (!status.canGoTo(next)) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION",
                    "An order that is " + status + " cannot become " + next);
        }
        this.status = next;
    }

    private void requireStatus(OrderStatus expected, String code, String message) {
        if (status != expected) throw ApiException.conflict(code, message);
    }

    public void requireDraft() {
        requireStatus(OrderStatus.DRAFT, "ORDER_NOT_EDITABLE", "Only a draft order can be changed or deleted");
    }

    private void recalculate() {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderLine l : lines) sum = sum.add(l.getLineTotal());
        this.subtotal = sum;
        this.taxAmount = BigDecimal.ZERO;
        this.totalAmount = sum.add(taxAmount);
    }
}
