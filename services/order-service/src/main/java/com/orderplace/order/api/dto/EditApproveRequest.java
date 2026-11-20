package com.orderplace.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Only quantities can be changed (never prices, never new products). Remarks are required.
 * approvedQty 0 removes the line. Lines that are not listed keep their submitted quantity.
 */
public record EditApproveRequest(
        Long version,
        @NotBlank @Size(max = 1000) String remarks,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(@NotNull UUID lineId, @Min(0) int approvedQty) {}
}
