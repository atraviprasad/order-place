package com.orderplace.payment.service;

import com.orderplace.common.web.PagedResponse;
import com.orderplace.payment.api.dto.PaymentResponse;
import com.orderplace.payment.model.Payment;
import com.orderplace.payment.model.PaymentStatus;
import com.orderplace.payment.gateway.PaymentGatewayClient;
import com.orderplace.payment.repository.PaymentRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only list of all payments for admins. */
@Service
@Transactional(readOnly = true)
public class AdminPaymentService {

    private final PaymentRepository payments;
    private final PaymentGatewayClient gateway;

    public AdminPaymentService(PaymentRepository payments, PaymentGatewayClient gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    public PagedResponse<PaymentResponse> list(PaymentStatus status, UUID orderId, UUID customerId,
                                               LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (orderId != null) ps.add(cb.equal(root.get("orderId"), orderId));
            if (customerId != null) ps.add(cb.equal(root.get("customerId"), customerId));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.<Instant>get("initiatedAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (to != null) ps.add(cb.lessThan(root.<Instant>get("initiatedAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Payment> page = payments.findAll(spec, pageable);
        return new PagedResponse<>(page.getContent().stream().map(p -> PaymentResponse.of(p, gateway.keyId())).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
