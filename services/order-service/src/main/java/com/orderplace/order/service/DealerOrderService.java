package com.orderplace.order.service;

import com.orderplace.common.events.EventTypes;
import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.order.api.dto.ApproveRequest;
import com.orderplace.order.api.dto.EditApproveRequest;
import com.orderplace.order.api.dto.OrderResponse;
import com.orderplace.order.api.dto.OrderStatusSummary;
import com.orderplace.order.api.dto.OrderSummary;
import com.orderplace.order.api.dto.RejectRequest;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.DealerAction;
import com.orderplace.order.model.DealerActionType;
import com.orderplace.order.model.OrderLine;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.model.OrderStatusHistory;
import com.orderplace.order.mapper.OrderMapper;
import com.orderplace.order.outbox.OutboxWriter;
import com.orderplace.order.repository.DealerActionRepository;
import com.orderplace.order.repository.OrderRepository;
import com.orderplace.order.repository.OrderStatusHistoryRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dealer-side use cases. The dealer is always taken from the JWT (org_id); an order of another dealer, and any
 * draft, is reported as "not found". A dealer can only change quantities: prices and products are never editable.
 */
@Service
@Transactional(readOnly = true)
public class DealerOrderService {

    private static final String DEALER = "DEALER";

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final DealerActionRepository actions;
    private final OutboxWriter outbox;

    public DealerOrderService(OrderRepository orders, OrderStatusHistoryRepository history,
                              DealerActionRepository actions, OutboxWriter outbox) {
        this.orders = orders;
        this.history = history;
        this.actions = actions;
        this.outbox = outbox;
    }

    public PagedResponse<OrderSummary> list(TenantContext ctx, String q, OrderStatus status, UUID customerId,
                                            LocalDate from, LocalDate to, Pageable pageable) {
        requireDealer(ctx);
        var filter = new OrderSpecs.Filter(customerId, ctx.orgId(), status, q, from, to, true);
        Page<CustomerOrder> page = orders.findAll(OrderSpecs.of(filter), pageable);
        return new PagedResponse<>(page.getContent().stream().map(OrderMapper::toSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public OrderStatusSummary summary(TenantContext ctx) {
        requireDealer(ctx);
        return StatusSummaries.build(orders.countByStatusForDealer(ctx.orgId(), OrderStatus.DRAFT));
    }

    public OrderResponse get(TenantContext ctx, UUID id) {
        CustomerOrder order = find(ctx, id);
        return view(order);
    }

    @Transactional
    public OrderResponse startReview(TenantContext ctx, UUID id, Long version) {
        CustomerOrder order = find(ctx, id);
        checkVersion(order, version);
        OrderStatus from = order.getStatus();
        order.startReview();
        orders.saveAndFlush(order);
        history.save(OrderStatusHistory.of(order.getId(), from, order.getStatus(), userId(ctx), DEALER, null));
        return view(order);
    }

    @Transactional
    public OrderResponse approve(TenantContext ctx, UUID id, ApproveRequest r) {
        CustomerOrder order = find(ctx, id);
        checkVersion(order, r.version());
        String remarks = blankToNull(r.remarks());
        OrderStatus from = order.getStatus();
        order.approve(Map.of());
        return finish(ctx, order, from, DealerActionType.APPROVE, remarks, EventTypes.ORDER_APPROVED);
    }

    @Transactional
    public OrderResponse reject(TenantContext ctx, UUID id, RejectRequest r) {
        CustomerOrder order = find(ctx, id);
        checkVersion(order, r.version());
        String reason = r.reason().strip();
        OrderStatus from = order.getStatus();
        order.reject();
        return finish(ctx, order, from, DealerActionType.REJECT, reason, EventTypes.ORDER_REJECTED);
    }

    @Transactional
    public OrderResponse editAndApprove(TenantContext ctx, UUID id, EditApproveRequest r) {
        CustomerOrder order = find(ctx, id);
        checkVersion(order, r.version());
        if (order.getStatus() != OrderStatus.UNDER_REVIEW) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION",
                    "An order that is " + order.getStatus() + " cannot be edited and approved");
        }
        Map<UUID, OrderLine> byId = new HashMap<>();
        for (OrderLine l : order.getLines()) byId.put(l.getId(), l);

        Map<UUID, Integer> approved = new HashMap<>();
        boolean changed = false;
        for (EditApproveRequest.Line line : r.lines()) {
            OrderLine target = byId.get(line.lineId());
            if (target == null) {
                throw ApiException.badRequest("Line does not belong to this order: " + line.lineId());
            }
            if (approved.put(line.lineId(), line.approvedQty()) != null) {
                throw ApiException.badRequest("The same line appears twice: " + line.lineId());
            }
            if (line.approvedQty() > target.getSubmittedQty()) {
                throw ApiException.unprocessable("QTY_EXCEEDS_SUBMITTED",
                        "You can only reduce quantities. " + target.getSku() + " was ordered as " + target.getSubmittedQty());
            }
            if (line.approvedQty() != target.getSubmittedQty()) changed = true;
        }
        if (!changed) {
            throw ApiException.unprocessable("NO_CHANGES", "Nothing was changed. Use approve instead.");
        }
        boolean anyLeft = order.getLines().stream()
                .anyMatch(l -> approved.getOrDefault(l.getId(), l.getSubmittedQty()) > 0);
        if (!anyLeft) {
            throw ApiException.unprocessable("NO_LINES_APPROVED", "All lines were removed. Use reject instead.");
        }

        String remarks = r.remarks().strip();
        OrderStatus from = order.getStatus();
        order.approve(approved);
        return finish(ctx, order, from, DealerActionType.EDIT, remarks, EventTypes.ORDER_EDITED);
    }

    // ---- helpers

    private OrderResponse finish(TenantContext ctx, CustomerOrder order, OrderStatus from, DealerActionType type,
                                 String remarks, String eventType) {
        orders.saveAndFlush(order);
        history.save(OrderStatusHistory.of(order.getId(), from, order.getStatus(), userId(ctx), DEALER, remarks));
        DealerAction action = actions.save(DealerAction.of(order.getId(), type, userId(ctx), remarks));
        outbox.orderEvent(eventType, order, remarks);
        return OrderMapper.toResponse(order, action);
    }

    private OrderResponse view(CustomerOrder order) {
        return OrderMapper.toResponse(order, actions.findFirstByOrderIdOrderByActedAtDesc(order.getId()).orElse(null));
    }

    private CustomerOrder find(TenantContext ctx, UUID id) {
        requireDealer(ctx);
        return orders.findByIdAndDealerId(id, ctx.orgId())
                .filter(o -> o.getStatus() != OrderStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
    }

    private static void requireDealer(TenantContext ctx) {
        if (!ctx.isDealer() || ctx.orgId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only users of a dealer organisation can do this");
        }
    }

    private static void checkVersion(CustomerOrder order, Long version) {
        if (version != null && !version.equals(order.getVersion())) {
            throw ApiException.conflict("VERSION_CONFLICT", "Order already processed or changed by someone else. Reload it.");
        }
    }

    private static UUID userId(TenantContext ctx) {
        return UUID.fromString(ctx.userId());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
