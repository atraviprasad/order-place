package com.orderplace.user.repository;

import com.orderplace.user.model.Customer;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByCustomerCodeIgnoreCase(String customerCode);

    boolean existsByNameIgnoreCaseAndEmailIgnoreCase(String name, String email);

    boolean existsByNameIgnoreCaseAndEmailIgnoreCaseAndIdNot(String name, String email, UUID id);

    /** q must already be lower case and wrapped in % signs. */
    @Query("select c from Customer c where lower(c.name) like :q or lower(c.customerCode) like :q")
    Page<Customer> search(@Param("q") String q, Pageable pageable);

    @Query("select c from Customer c where c.active = :active and (lower(c.name) like :q or lower(c.customerCode) like :q)")
    Page<Customer> searchByActive(@Param("q") String q, @Param("active") boolean active, Pageable pageable);
}
