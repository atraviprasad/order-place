package com.orderplace.order.api.dto;

import java.util.List;

/** Admin view: the order plus its full history. Payment status will be added with the Payment Service. */
public record OrderDetailResponse(OrderResponse order, List<HistoryEntry> history) {}
