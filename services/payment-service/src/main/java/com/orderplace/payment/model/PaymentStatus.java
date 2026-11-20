package com.orderplace.payment.model;

import java.util.List;

/**
 * INITIATED = gateway order created, customer has not paid yet.
 * PENDING_CONFIRMATION = the browser reported a valid result, the webhook has not confirmed it yet.
 * SUCCESS = confirmed by the signed webhook (final). FAILED = the gateway reported a failed attempt.
 */
public enum PaymentStatus {
    INITIATED, PENDING_CONFIRMATION, SUCCESS, FAILED, EXPIRED;

    public static final List<PaymentStatus> OPEN = List.of(INITIATED, PENDING_CONFIRMATION);
}
