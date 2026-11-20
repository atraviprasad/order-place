package com.orderplace.payment.api;

import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.payment.api.dto.CallbackResponse;
import com.orderplace.payment.api.dto.ClientCallbackRequest;
import com.orderplace.payment.api.dto.CreatePaymentRequest;
import com.orderplace.payment.api.dto.PaymentResponse;
import com.orderplace.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Served under /api/v1 (server.servlet.context-path). */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> start(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody CreatePaymentRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw ApiException.badRequest("Idempotency-Key must be 1 to 100 characters");
        }
        PaymentService.Result result = service.start(TenantContext.from(jwt), jwt.getClaimAsString("email"),
                request.orderId(), idempotencyKey.strip());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/payments/" + result.payment().paymentId())).body(result.payment());
        }
        return ResponseEntity.ok(result.payment());
    }

    @PostMapping("/{id}/client-callback")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CallbackResponse clientCallback(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @Valid @RequestBody ClientCallbackRequest request) {
        return service.clientCallback(TenantContext.from(jwt), id, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(TenantContext.from(jwt), id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public List<PaymentResponse> listForOrder(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID orderId) {
        return service.listForOrder(TenantContext.from(jwt), orderId);
    }
}
