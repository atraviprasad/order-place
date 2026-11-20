package com.orderplace.order.api.dto;

import com.orderplace.order.model.OrderStatus;
import java.util.Map;

public record OrderStatusSummary(Map<OrderStatus, Long> counts, long total) {}
