package com.orderplace.catalog.repository;

import com.orderplace.catalog.model.ProductImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAscCreatedAtAsc(UUID productId);

    List<ProductImage> findByProductIdInAndPrimaryImageTrue(Collection<UUID> productIds);

    Optional<ProductImage> findByIdAndProductId(UUID id, UUID productId);

    Optional<ProductImage> findByProductIdAndPrimaryImageTrue(UUID productId);

    Optional<ProductImage> findFirstByProductIdOrderBySortOrderAscCreatedAtAsc(UUID productId);

    long countByProductId(UUID productId);

    @Query("select coalesce(max(i.sortOrder), 0) from ProductImage i where i.productId = :productId")
    int maxSortOrder(@Param("productId") UUID productId);
}
