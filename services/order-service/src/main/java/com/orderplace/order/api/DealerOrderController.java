package com.orderplace.order.api;

import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.PageParams;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.order.api.dto.ApproveRequest;
import com.orderplace.order.api.dto.EditApproveRequest;
import com.orderplace.order.api.dto.OrderResponse;
import com.orderplace.order.api.dto.OrderStatusSummary;
import com.orderplace.order.api.dto.OrderSummary;
import com.orderplace.order.api.dto.RejectRequest;
import com.orderplace.order.api.dto.VersionRequest;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.service.DealerOrderService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dealer endpoints. The dealer is always the organisation in the token. */
@RestController
@RequestMapping("/dealer/orders")
@PreAuthorize("hasRole('DEALER')")
public class DealerOrderController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "submittedAt", "orderNumber", "totalAmount", "status");

    private final DealerOrderService service;

    public DealerOrderController(DealerOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<OrderSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "submittedAt,desc") String sort) {
        return service.list(TenantContext.from(jwt), q, status, customerId, from, to,
                PageParams.of(page, size, sort, SORTABLE));
    }

    @GetMapping("/summary")
    public OrderStatusSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(TenantContext.from(jwt));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(TenantContext.from(jwt), id);
    }

    @PostMapping("/{id}/start-review")
    public OrderResponse startReview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @RequestBody(required = false) VersionRequest request) {
        return service.startReview(TenantContext.from(jwt), id, request == null ? null : request.version());
    }

    @PostMapping("/{id}/approve")
    public OrderResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody(required = false) ApproveRequest request) {
        return service.approve(TenantContext.from(jwt), id, request == null ? new ApproveRequest(null, null) : request);
    }

    @PostMapping("/{id}/reject")
    public OrderResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody RejectRequest request) {
        return service.reject(TenantContext.from(jwt), id, request);
    }

    @PostMapping("/{id}/edit-and-approve")
    public OrderResponse editAndApprove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody EditApproveRequest request) {
        return service.editAndApprove(TenantContext.from(jwt), id, request);
    }
}
