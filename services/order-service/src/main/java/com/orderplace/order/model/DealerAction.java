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

/** A dealer's decision on an order (approve, reject or edit-and-approve). Append-only. */
@Entity
@Table(name = "dealer_action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DealerAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 10)
    private DealerActionType action;

    @Column(name = "dealer_user_id", nullable = false, updatable = false)
    private UUID dealerUserId;

    @Column(name = "remarks", updatable = false, columnDefinition = "text")
    private String remarks;

    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static DealerAction of(UUID orderId, DealerActionType action, UUID dealerUserId, String remarks) {
        DealerAction a = new DealerAction();
        a.orderId = orderId;
        a.action = action;
        a.dealerUserId = dealerUserId;
        a.remarks = remarks;
        a.actedAt = Instant.now();
        return a;
    }
}
