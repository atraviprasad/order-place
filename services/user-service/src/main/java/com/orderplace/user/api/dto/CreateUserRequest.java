package com.orderplace.user.api.dto;

import com.orderplace.user.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** orgId is a customer id for role CUSTOMER and a dealer id for role DEALER. The email is the login name. */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 30) String phone,
        @NotNull UserRole role,
        @NotNull UUID orgId) {}
