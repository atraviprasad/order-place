package com.orderplace.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Replaces dealer, address, notes and lines of a draft. Send the version you loaded to detect concurrent edits. */
public record SaveOrderRequest(
        Long version,
        UUID dealerId,
        @Size(max = 1000) String deliveryAddress,
        @Size(max = 2000) String customerNotes,
        @Valid @Size(max = 50) List<OrderLineRequest> lines) {}
