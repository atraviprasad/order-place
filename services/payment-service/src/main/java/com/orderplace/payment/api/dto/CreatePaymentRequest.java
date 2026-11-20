package com.orderplace.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** No amount: the server takes it from the order. */
public record CreatePaymentRequest(@NotNull UUID orderId) {}
