package com.orderplace.user.api.dto;

import java.util.UUID;

/** Identity of the caller, taken from the JWT. orgName is filled for customer users only. */
public record MeResponse(
        String userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String role,
        String orgType,
        UUID orgId,
        String orgName) {}
