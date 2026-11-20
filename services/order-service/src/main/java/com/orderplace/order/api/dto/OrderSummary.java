package com.orderplace.order.api.dto;

import com.orderplace.order.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of an order list. */
public record OrderSummary(UUID id, String orderNumber, OrderStatus status, UUID customerId, String customerName,
                           UUID dealerId, String dealerName, BigDecimal totalAmount, String currency,
                           Instant submittedAt, Instant createdAt, Long version) {}
