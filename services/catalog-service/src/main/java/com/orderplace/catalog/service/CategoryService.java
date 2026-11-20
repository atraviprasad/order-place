package com.orderplace.catalog.service;

import com.orderplace.catalog.api.dto.CategoryNode;
import com.orderplace.catalog.api.dto.CategoryResponse;
import com.orderplace.catalog.api.dto.CreateCategoryRequest;
import com.orderplace.catalog.api.dto.UpdateCategoryRequest;
import com.orderplace.catalog.model.Category;
import com.orderplace.catalog.repository.CategoryRepository;
import com.orderplace.catalog.repository.ProductRepository;
import com.orderplace.common.web.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final String DUPLICATE = "DUPLICATE_RESOURCE";

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CategoryTreeReader treeReader;
    private final CatalogCache cache;

    public CategoryService(CategoryRepository categories, ProductRepository products,
                           CategoryTreeReader treeReader, CatalogCache cache) {
        this.categories = categories;
        this.products = products;
        this.treeReader = treeReader;
        this.cache = cache;
    }

    /** Admins see every category; everybody else sees only active ones (an inactive parent hides its children). */
    public List<CategoryNode> tree(boolean admin) {
        List<CategoryNode> full = treeReader.fullTree();
        return admin ? full : activeOnly(full);
    }

    private static List<CategoryNode> activeOnly(List<CategoryNode> nodes) {
        List<CategoryNode> result = new ArrayList<>();
        for (CategoryNode n : nodes) {
            if (n.active()) {
                result.add(new CategoryNode(n.id(), n.name(), n.sortOrder(), true, activeOnly(n.children())));
            }
        }
        return result;
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest r) {
        String name = r.name().strip();
        if (r.parentId() != null) checkParentUsable(r.parentId());
        if (nameTaken(r.parentId(), name, null)) {
            throw ApiException.conflict(DUPLICATE, "A category named '" + name + "' already exists here");
        }
        Category category = Category.create(r.parentId(), name, r.sortOrder() == null ? 0 : r.sortOrder());
        Category saved = saveAndFlush(category);
        cache.evictCategoryTree();
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest r) {
        Category category = find(id);
        if (r.version() != null && !r.version().equals(category.getVersion())) {
            throw ApiException.conflict("VERSION_CONFLICT", "This category was changed by someone else. Reload and try again.");
        }
        String name = r.name().strip();
        if (r.parentId() != null && !r.parentId().equals(category.getParentId())) {
            checkParentUsable(r.parentId());
            checkNoCycle(id, r.parentId());
        }
        if (nameTaken(r.parentId(), name, id)) {
            throw ApiException.conflict(DUPLICATE, "A category named '" + name + "' already exists here");
        }
        category.update(r.parentId(), name, r.sortOrder() == null ? category.getSortOrder() : r.sortOrder());
        Category saved = saveAndFlush(category);
        cache.evictCategoryTree();
        cache.evictAllProducts();   // cached products carry the category name
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse deactivate(UUID id) {
        Category category = find(id);
        if (!category.isActive()) return toResponse(category);
        if (categories.existsByParentIdAndActiveTrue(id)) {
            throw ApiException.unprocessable("CATEGORY_NOT_EMPTY", "Deactivate or move its active sub-categories first");
        }
        if (products.existsByCategoryIdAndActiveTrue(id)) {
            throw ApiException.unprocessable("CATEGORY_NOT_EMPTY", "Deactivate or move its active products first");
        }
        category.deactivate();
        Category saved = saveAndFlush(category);
        cache.evictCategoryTree();
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse activate(UUID id) {
        Category category = find(id);
        if (category.isActive()) return toResponse(category);
        if (category.getParentId() != null) checkParentUsable(category.getParentId());
        category.activate();
        Category saved = saveAndFlush(category);
        cache.evictCategoryTree();
        return toResponse(saved);
    }

    /** The category itself plus all its descendants. Used so that filtering by "Seating" also finds "Office Chairs". */
    public Set<UUID> subtreeIds(UUID rootId) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (Category c : categories.findAll()) {
            children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
        }
        Set<UUID> result = new HashSet<>();
        List<UUID> queue = new ArrayList<>(List.of(rootId));
        while (!queue.isEmpty()) {
            UUID next = queue.remove(queue.size() - 1);
            if (result.add(next)) queue.addAll(children.getOrDefault(next, List.of()));
        }
        return result;
    }

    private void checkParentUsable(UUID parentId) {
        Category parent = categories.findById(parentId)
                .orElseThrow(() -> ApiException.unprocessable("PARENT_NOT_FOUND", "Parent category not found: " + parentId));
        if (!parent.isActive()) {
            throw ApiException.unprocessable("PARENT_INACTIVE", "The parent category is inactive");
        }
    }

    /** A category cannot be moved under itself or under one of its own descendants. */
    private void checkNoCycle(UUID id, UUID newParentId) {
        Map<UUID, UUID> parentOf = new HashMap<>();
        for (Category c : categories.findAll()) parentOf.put(c.getId(), c.getParentId());
        UUID cursor = newParentId;
        Set<UUID> seen = new HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(id)) {
                throw ApiException.unprocessable("CATEGORY_CYCLE", "A category cannot be moved under itself or its own sub-category");
            }
            cursor = parentOf.get(cursor);
        }
    }

    private boolean nameTaken(UUID parentId, String name, UUID selfId) {
        if (selfId == null) {
            return parentId == null
                    ? categories.existsByParentIdIsNullAndNameIgnoreCase(name)
                    : categories.existsByParentIdAndNameIgnoreCase(parentId, name);
        }
        return parentId == null
                ? categories.existsByParentIdIsNullAndNameIgnoreCaseAndIdNot(name, selfId)
                : categories.existsByParentIdAndNameIgnoreCaseAndIdNot(parentId, name, selfId);
    }

    private Category find(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found: " + id));
    }

    private Category saveAndFlush(Category c) {
        try {
            return categories.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(DUPLICATE, "A category with this name already exists here");
        }
    }

    private static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSortOrder(), c.isActive(), c.getVersion());
    }
}
