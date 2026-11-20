package com.orderplace.order.repository;

import com.orderplace.order.model.CustomerOrder;
import com.orderplace.order.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID>, JpaSpecificationExecutor<CustomerOrder> {

    /** Tenant-safe lookup: an order of another customer is simply "not found". */
    Optional<CustomerOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    /** Tenant-safe lookup for dealers: an order of another dealer is simply "not found". */
    Optional<CustomerOrder> findByIdAndDealerId(UUID id, UUID dealerId);

    @Query("select o.status, count(o) from CustomerOrder o where o.dealerId = :dealerId and o.status <> :excluded group by o.status")
    List<Object[]> countByStatusForDealer(@Param("dealerId") UUID dealerId, @Param("excluded") OrderStatus excluded);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    @Query("select o.status, count(o) from CustomerOrder o group by o.status")
    List<Object[]> countByStatus();
}
