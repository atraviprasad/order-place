package com.orderplace.user.repository;

import com.orderplace.user.model.AppUser;
import com.orderplace.user.model.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    /** Users of an organisation who are individually active (used when a customer is deactivated or re-activated). */
    List<AppUser> findByOrgIdAndActiveTrue(UUID orgId);

    List<AppUser> findByOrgIdOrderByLastNameAscFirstNameAsc(UUID orgId);

    // q must already be lower case and wrapped in % signs. Four variants avoid null-parameter problems on PostgreSQL.
    String MATCH = "(lower(u.email) like :q or lower(u.firstName) like :q or lower(u.lastName) like :q)";

    @Query("select u from AppUser u where " + MATCH)
    Page<AppUser> search(@Param("q") String q, Pageable pageable);

    @Query("select u from AppUser u where u.role = :role and " + MATCH)
    Page<AppUser> searchByRole(@Param("q") String q, @Param("role") UserRole role, Pageable pageable);

    @Query("select u from AppUser u where u.active = :active and " + MATCH)
    Page<AppUser> searchByActive(@Param("q") String q, @Param("active") boolean active, Pageable pageable);

    @Query("select u from AppUser u where u.role = :role and u.active = :active and " + MATCH)
    Page<AppUser> searchByRoleAndActive(@Param("q") String q, @Param("role") UserRole role,
            @Param("active") boolean active, Pageable pageable);
}
