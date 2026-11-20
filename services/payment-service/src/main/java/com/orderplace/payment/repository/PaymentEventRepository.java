package com.orderplace.payment.repository;

import com.orderplace.payment.model.PaymentEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
    boolean existsByGatewayEventId(String gatewayEventId);
}
