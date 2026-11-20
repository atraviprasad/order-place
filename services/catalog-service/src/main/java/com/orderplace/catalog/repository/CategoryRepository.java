package com.orderplace.catalog.repository;

import com.orderplace.catalog.model.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByParentIdAndNameIgnoreCase(UUID parentId, String name);

    boolean existsByParentIdIsNullAndNameIgnoreCase(String name);

    boolean existsByParentIdAndNameIgnoreCaseAndIdNot(UUID parentId, String name, UUID id);

    boolean existsByParentIdIsNullAndNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByParentIdAndActiveTrue(UUID parentId);
}
