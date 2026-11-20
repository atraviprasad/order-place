package com.orderplace.catalog.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceHistoryResponse(BigDecimal oldPrice, BigDecimal newPrice, String changedBy, Instant changedAt) {}
