package com.orderplace.order.api;

import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PageParams;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.order.api.dto.CancelRequest;
import com.orderplace.order.api.dto.CreateOrderRequest;
import com.orderplace.order.api.dto.HistoryEntry;
import com.orderplace.order.api.dto.OrderResponse;
import com.orderplace.order.api.dto.OrderSummary;
import com.orderplace.order.api.dto.SaveOrderRequest;
import com.orderplace.order.api.dto.SubmitRequest;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer endpoints. Served under /api/v1 (server.servlet.context-path). */
@RestController
@RequestMapping("/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class OrderController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "submittedAt", "orderNumber", "totalAmount", "status");

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody(required = false) CreateOrderRequest request) {
        CreateOrderRequest body = request == null ? new CreateOrderRequest(null, null, null, null) : request;
        OrderResponse created = service.createDraft(TenantContext.from(jwt), body);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + created.id())).body(created);
    }

    @GetMapping
    public PagedResponse<OrderSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID dealerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(TenantContext.from(jwt), q, status, dealerId, from, to,
                PageParams.of(page, size, sort, SORTABLE));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(TenantContext.from(jwt), id);
    }

    @PutMapping("/{id}")
    public OrderResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody SaveOrderRequest request) {
        return service.updateDraft(TenantContext.from(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteDraft(TenantContext.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    public OrderResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                @RequestBody(required = false) SubmitRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw ApiException.badRequest("Idempotency-Key must be 1 to 100 characters");
        }
        return service.submit(TenantContext.from(jwt), id, idempotencyKey.strip(), request == null ? null : request.version());
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody(required = false) CancelRequest request) {
        return service.cancel(TenantContext.from(jwt), id, request);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.history(TenantContext.from(jwt), id);
    }
}
