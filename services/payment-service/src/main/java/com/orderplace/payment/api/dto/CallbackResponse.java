package com.orderplace.payment.api.dto;

import com.orderplace.payment.model.PaymentStatus;

public record CallbackResponse(PaymentStatus status) {}
