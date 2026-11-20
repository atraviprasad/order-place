package com.orderplace.common.events;

import java.math.BigDecimal;
import java.util.UUID;

/** Minimal order payload shared by order events. Add fields as the contract evolves (bump eventVersion for breaking changes). */
public record OrderEventPayload(
        UUID orderId,
        String orderNumber,
        UUID customerId,
        String customerName,
        UUID dealerId,
        String customerEmail,
        String dealerEmail,
        BigDecimal total,
        String currency,
        String reason) {}
