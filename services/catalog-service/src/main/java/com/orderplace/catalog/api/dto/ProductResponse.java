package com.orderplace.catalog.api.dto;

import com.orderplace.catalog.model.Availability;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Full product detail. Serializable because it is cached in Redis. */
public record ProductResponse(
        UUID id, String sku, String name, String description, UUID categoryId, String categoryName,
        BigDecimal unitPrice, String currency, String unitOfMeasure, Availability availability, int minOrderQty,
        Map<String, Object> attributes, boolean active, Long version, List<ImageResponse> images,
        Instant createdAt, Instant updatedAt) implements Serializable {}
