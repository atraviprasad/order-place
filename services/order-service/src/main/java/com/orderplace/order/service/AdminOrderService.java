package com.orderplace.order.service;

import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.order.api.dto.OrderDetailResponse;
import com.orderplace.order.api.dto.OrderStatusSummary;
import com.orderplace.order.api.dto.OrderSummary;
import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.mapper.OrderMapper;
import com.orderplace.order.repository.DealerActionRepository;
import com.orderplace.order.repository.OrderRepository;
import com.orderplace.order.repository.OrderStatusHistoryRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only admin view across all customers and dealers. Other people's drafts are hidden unless asked for. */
@Service
@Transactional(readOnly = true)
public class AdminOrderService {

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final DealerActionRepository actions;

    public AdminOrderService(OrderRepository orders, OrderStatusHistoryRepository history, DealerActionRepository actions) {
        this.orders = orders;
        this.history = history;
        this.actions = actions;
    }

    public PagedResponse<OrderSummary> list(UUID customerId, UUID dealerId, OrderStatus status, String q,
                                            LocalDate from, LocalDate to, Pageable pageable) {
        var filter = new OrderSpecs.Filter(customerId, dealerId, status, q, from, to, true);
        Page<CustomerOrder> page = orders.findAll(OrderSpecs.of(filter), pageable);
        return new PagedResponse<>(page.getContent().stream().map(OrderMapper::toSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public OrderDetailResponse get(UUID id) {
        CustomerOrder order = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        return new OrderDetailResponse(
                OrderMapper.toResponse(order, actions.findFirstByOrderIdOrderByActedAtDesc(id).orElse(null)),
                history.findByOrderIdOrderByChangedAtAsc(id).stream().map(OrderMapper::toHistory).toList());
    }

    /** Every status is present (0 when there are no orders), drafts are not counted. */
    public OrderStatusSummary summary() {
        return StatusSummaries.build(orders.countByStatus());
    }
}

