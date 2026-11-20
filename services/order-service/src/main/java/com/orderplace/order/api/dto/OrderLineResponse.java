package com.orderplace.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineResponse(UUID id, int lineNo, UUID productId, String sku, String productName,
                                BigDecimal unitPrice, int submittedQty, Integer approvedQty, BigDecimal lineTotal) {}
