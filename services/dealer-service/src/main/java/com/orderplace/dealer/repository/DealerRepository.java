package com.orderplace.dealer.repository;

import com.orderplace.dealer.model.Dealer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DealerRepository extends JpaRepository<Dealer, UUID> {
    boolean existsByDealerCodeIgnoreCase(String dealerCode);

    boolean existsByNameIgnoreCaseAndEmailIgnoreCase(String name, String email);

    boolean existsByNameIgnoreCaseAndEmailIgnoreCaseAndIdNot(String name, String email, UUID id);

    List<Dealer> findByActiveTrueOrderByNameAsc();

    /** q must already be lower case and wrapped in % signs. */
    @Query("select d from Dealer d where lower(d.name) like :q or lower(d.dealerCode) like :q")
    Page<Dealer> search(@Param("q") String q, Pageable pageable);

    @Query("select d from Dealer d where d.active = :active and (lower(d.name) like :q or lower(d.dealerCode) like :q)")
    Page<Dealer> searchByActive(@Param("q") String q, @Param("active") boolean active, Pageable pageable);
}
