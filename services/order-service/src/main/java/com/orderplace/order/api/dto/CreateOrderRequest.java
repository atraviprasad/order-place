package com.orderplace.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Everything is optional: a draft may start empty. The delivery address defaults to the customer's own. */
public record CreateOrderRequest(
        UUID dealerId,
        @Size(max = 1000) String deliveryAddress,
        @Size(max = 2000) String customerNotes,
        @Valid @Size(max = 50) List<OrderLineRequest> lines) {}
