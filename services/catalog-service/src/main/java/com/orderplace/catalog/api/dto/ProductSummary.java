package com.orderplace.catalog.api.dto;

import com.orderplace.catalog.model.Availability;
import java.math.BigDecimal;
import java.util.UUID;

/** One row of the product list: no description or attributes, only the primary image. */
public record ProductSummary(
        UUID id, String sku, String name, UUID categoryId, BigDecimal unitPrice, String currency,
        String unitOfMeasure, Availability availability, int minOrderQty, boolean active, String primaryImageUrl) {}
