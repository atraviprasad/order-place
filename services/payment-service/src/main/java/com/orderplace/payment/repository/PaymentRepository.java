package com.orderplace.payment.repository;

import com.orderplace.payment.model.Payment;
import com.orderplace.payment.model.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByGatewayNameAndGatewayOrderRef(String gatewayName, String gatewayOrderRef);
    Optional<Payment> findFirstByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);
    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);
    List<Payment> findByOrderIdOrderByInitiatedAtDesc(UUID orderId);
}
