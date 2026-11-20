package com.orderplace.order.service;

import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds the list filter shared by the customer and admin lists. from/to (inclusive) apply to the creation date. */
final class OrderSpecs {
    private OrderSpecs() {}

    record Filter(UUID customerId, UUID dealerId, OrderStatus status, String q, LocalDate from, LocalDate to,
                  boolean hideDrafts) {}

    static Specification<CustomerOrder> of(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.customerId() != null) ps.add(cb.equal(root.get("customerId"), f.customerId()));
            if (f.dealerId() != null) ps.add(cb.equal(root.get("dealerId"), f.dealerId()));
            if (f.status() != null) {
                ps.add(cb.equal(root.get("status"), f.status()));
            } else if (f.hideDrafts()) {
                ps.add(cb.notEqual(root.get("status"), OrderStatus.DRAFT));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String pattern = "%" + f.q().strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.like(cb.lower(root.get("orderNumber")), pattern, '\\'));
            }
            if (f.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), f.from().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (f.to() != null) {
                ps.add(cb.lessThan(root.<Instant>get("createdAt"), f.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
