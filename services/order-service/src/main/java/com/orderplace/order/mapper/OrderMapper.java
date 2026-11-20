package com.orderplace.order.mapper;

import com.orderplace.order.api.dto.HistoryEntry;
import com.orderplace.order.api.dto.OrderLineResponse;
import com.orderplace.order.api.dto.OrderResponse;
import com.orderplace.order.api.dto.OrderSummary;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.DealerAction;
import com.orderplace.order.model.OrderStatusHistory;

public final class OrderMapper {
    private OrderMapper() {}

    public static OrderResponse toResponse(CustomerOrder o) {
        return toResponse(o, null);
    }

    public static OrderResponse toResponse(CustomerOrder o, DealerAction action) {
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getCustomerId(), o.getCustomerName(),
                o.getDealerId() == null ? null : new OrderResponse.DealerRef(o.getDealerId(), o.getDealerName()),
                o.getDeliveryAddress(), o.getCustomerNotes(),
                o.getLines().stream().sorted(java.util.Comparator.comparingInt(l -> l.getLineNo())).map(l -> new OrderLineResponse(l.getId(), l.getLineNo(), l.getProductId(),
                        l.getSku(), l.getProductName(), l.getUnitPrice(), l.getSubmittedQty(), l.getApprovedQty(),
                        l.getLineTotal())).toList(),
                o.getSubtotal(), o.getTaxAmount(), o.getTotalAmount(), o.getCurrency(),
                o.getSubmittedAt(), o.getCreatedAt(), o.getUpdatedAt(), o.getVersion(),
                action == null ? null : new OrderResponse.DealerActionRef(action.getAction().name(), action.getRemarks(), action.getActedAt()));
    }

    public static OrderSummary toSummary(CustomerOrder o) {
        return new OrderSummary(o.getId(), o.getOrderNumber(), o.getStatus(), o.getCustomerId(), o.getCustomerName(),
                o.getDealerId(), o.getDealerName(), o.getTotalAmount(), o.getCurrency(),
                o.getSubmittedAt(), o.getCreatedAt(), o.getVersion());
    }

    public static HistoryEntry toHistory(OrderStatusHistory h) {
        return new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getChangedByRole(), h.getChangedByUserId(),
                h.getReason(), h.getChangedAt());
    }
}
