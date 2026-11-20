package com.orderplace.payment.api;

import com.orderplace.common.web.PageParams;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.payment.api.dto.PaymentResponse;
import com.orderplace.payment.model.PaymentStatus;
import com.orderplace.payment.service.AdminPaymentService;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private static final Set<String> SORTABLE = Set.of("initiatedAt", "completedAt", "amount", "status");

    private final AdminPaymentService service;

    public AdminPaymentController(AdminPaymentService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<PaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "initiatedAt,desc") String sort) {
        return service.list(status, orderId, customerId, from, to, PageParams.of(page, size, sort, SORTABLE));
    }
}
