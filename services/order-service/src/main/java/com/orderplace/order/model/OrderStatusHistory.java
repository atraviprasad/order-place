package com.orderplace.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only timeline of status changes. */
@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 30)
    private OrderStatus toStatus;

    @Column(name = "changed_by_user_id", updatable = false)
    private UUID changedByUserId;

    @Column(name = "changed_by_role", nullable = false, updatable = false, length = 20)
    private String changedByRole;

    @Column(name = "reason", updatable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public static OrderStatusHistory of(UUID orderId, OrderStatus from, OrderStatus to, UUID userId, String role, String reason) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.orderId = orderId;
        h.fromStatus = from;
        h.toStatus = to;
        h.changedByUserId = userId;
        h.changedByRole = role;
        h.reason = reason;
        h.changedAt = Instant.now();
        return h;
    }
}
