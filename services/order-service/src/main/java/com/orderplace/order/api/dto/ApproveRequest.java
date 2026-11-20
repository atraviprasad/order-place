package com.orderplace.order.api.dto;

import jakarta.validation.constraints.Size;

public record ApproveRequest(Long version, @Size(max = 1000) String remarks) {}
