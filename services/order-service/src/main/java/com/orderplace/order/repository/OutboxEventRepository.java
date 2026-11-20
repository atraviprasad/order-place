package com.orderplace.order.repository;

import com.orderplace.order.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt asc")
    List<OutboxEvent> findUnpublished(Pageable pageable);

    @Modifying
    @Transactional
    @Query("update OutboxEvent e set e.publishedAt = :now, e.attempts = e.attempts + 1, e.lastError = null where e.id = :id")
    int markPublished(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("update OutboxEvent e set e.attempts = e.attempts + 1, e.lastError = :error where e.id = :id")
    int markFailed(@Param("id") UUID id, @Param("error") String error);
}
