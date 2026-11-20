package com.orderplace.order.client;

import java.math.BigDecimal;
import java.util.UUID;

/** What the Order Service needs to know about a catalog product. */
public record ProductInfo(UUID id, String sku, String name, BigDecimal unitPrice, boolean available,
                          int minOrderQty, boolean active) {}
