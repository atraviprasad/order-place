package com.orderplace.order.service;

import com.orderplace.order.api.dto.OrderStatusSummary;
import com.orderplace.order.model.OrderStatus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Turns "status, count" rows into a summary where every status is present and drafts are not counted. */
final class StatusSummaries {
    private StatusSummaries() {}

    static OrderStatusSummary build(List<Object[]> rows) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            if (s != OrderStatus.DRAFT) counts.put(s, 0L);
        }
        for (Object[] row : rows) {
            OrderStatus status = (OrderStatus) row[0];
            if (status != OrderStatus.DRAFT) counts.put(status, (Long) row[1]);
        }
        return new OrderStatusSummary(counts, counts.values().stream().mapToLong(Long::longValue).sum());
    }
}
