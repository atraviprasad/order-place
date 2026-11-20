package com.orderplace.catalog.service;

import com.orderplace.catalog.api.dto.CategoryNode;
import com.orderplace.catalog.model.Category;
import com.orderplace.catalog.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cached full category tree (active and inactive). Inactive nodes are filtered out after the cache for non-admins. */
@Component
public class CategoryTreeReader {

    private final CategoryRepository categories;

    public CategoryTreeReader(CategoryRepository categories) {
        this.categories = categories;
    }

    @Cacheable(cacheNames = CatalogCache.CATEGORY_TREE, key = "'all'")
    @Transactional(readOnly = true)
    public ArrayList<CategoryNode> fullTree() {
        Map<UUID, List<Category>> byParent = new HashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAscNameAsc()) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        return build(byParent, null);
    }

    private static ArrayList<CategoryNode> build(Map<UUID, List<Category>> byParent, UUID parentId) {
        ArrayList<CategoryNode> nodes = new ArrayList<>();
        for (Category c : byParent.getOrDefault(parentId, List.of())) {
            nodes.add(new CategoryNode(c.getId(), c.getName(), c.getSortOrder(), c.isActive(), build(byParent, c.getId())));
        }
        return nodes;
    }
}
