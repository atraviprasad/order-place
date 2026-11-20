package com.orderplace.user.api.dto;

import com.orderplace.user.model.UserRole;
import java.time.Instant;
import java.util.UUID;

/** orgName is filled for customer users only; the front end resolves dealer names from the dealer list. */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserRole role,
        UUID orgId,
        String orgName,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {}
