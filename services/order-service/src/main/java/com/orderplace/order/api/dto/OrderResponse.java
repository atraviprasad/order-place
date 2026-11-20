package com.orderplace.order.api.dto;

import com.orderplace.order.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id, String orderNumber, OrderStatus status, UUID customerId, String customerName,
        DealerRef dealer, String deliveryAddress, String customerNotes, List<OrderLineResponse> lines,
        BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount, String currency,
        Instant submittedAt, Instant createdAt, Instant updatedAt, Long version, DealerActionRef dealerAction) {

    public record DealerRef(UUID id, String name) {}

    /** The dealer's latest decision on this order (null until the dealer has acted). */
    public record DealerActionRef(String action, String remarks, Instant actedAt) {}
}
