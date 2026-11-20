package com.orderplace.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Email and role cannot be changed. orgId is optional: send it to move the user to another organisation of the same type. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 30) String phone,
        UUID orgId,
        Long version) {}
