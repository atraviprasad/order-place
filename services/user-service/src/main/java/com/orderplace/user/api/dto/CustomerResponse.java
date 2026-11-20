package com.orderplace.user.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerCode,
        String name,
        String contactPerson,
        String email,
        String phone,
        String billingAddress,
        String deliveryAddress,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {}
