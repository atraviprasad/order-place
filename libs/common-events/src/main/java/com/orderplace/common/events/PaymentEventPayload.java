package com.orderplace.common.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentEventPayload(
        UUID paymentId,
        UUID orderId,
        String orderNumber,
        String customerEmail,
        BigDecimal amount,
        String currency,
        String failureReason) {}
