package com.orderplace.order.api.dto;

import com.orderplace.order.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record HistoryEntry(OrderStatus fromStatus, OrderStatus toStatus, String changedByRole, UUID changedByUserId,
                           String reason, Instant changedAt) {}
