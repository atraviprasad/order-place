package com.orderplace.catalog.repository;

import com.orderplace.catalog.model.PriceHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {

    Page<PriceHistory> findByProductIdOrderByChangedAtDesc(UUID productId, Pageable pageable);
}
