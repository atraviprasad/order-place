package com.orderplace.order.model;

import java.math.BigDecimal;
import java.util.UUID;

/** What one order line should look like after a save: a product snapshot plus the quantity the customer wants. */
public record LineSpec(UUID productId, String sku, String productName, BigDecimal unitPrice, int quantity) {}
