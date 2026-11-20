package com.orderplace.order.repository;

import com.orderplace.order.model.DealerAction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerActionRepository extends JpaRepository<DealerAction, UUID> {
    Optional<DealerAction> findFirstByOrderIdOrderByActedAtDesc(UUID orderId);
}
