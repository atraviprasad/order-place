package com.orderplace.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderLineRequest(@NotNull UUID productId, @Min(1) @Max(100000) int quantity) {}
