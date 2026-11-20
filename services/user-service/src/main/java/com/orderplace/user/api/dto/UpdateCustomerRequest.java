package com.orderplace.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The customer code cannot be changed. Send the version you loaded to detect concurrent edits (optional). */
public record UpdateCustomerRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 150) String contactPerson,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 1000) String billingAddress,
        @NotBlank @Size(max = 1000) String deliveryAddress,
        Long version) {}
