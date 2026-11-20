package com.orderplace.order.api;

import com.orderplace.common.web.PageParams;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.order.api.dto.OrderDetailResponse;
import com.orderplace.order.api.dto.OrderStatusSummary;
import com.orderplace.order.api.dto.OrderSummary;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.service.AdminOrderService;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only admin endpoints. */
@RestController
@RequestMapping("/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "submittedAt", "orderNumber", "totalAmount", "status");

    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<OrderSummary> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID dealerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(customerId, dealerId, status, q, from, to, PageParams.of(page, size, sort, SORTABLE));
    }

    @GetMapping("/summary")
    public OrderStatusSummary summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    public OrderDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
