package com.orderplace.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(Long version, @NotBlank @Size(max = 1000) String reason) {}
