package com.orderplace.catalog.service;

import com.orderplace.catalog.api.dto.ProductResponse;
import com.orderplace.catalog.model.Category;
import com.orderplace.catalog.model.Product;
import com.orderplace.catalog.mapper.ProductMapper;
import com.orderplace.catalog.repository.CategoryRepository;
import com.orderplace.catalog.repository.ProductImageRepository;
import org.springframework.stereotype.Component;

/** Builds the full product response (category name + images) from the database. */
@Component
public class ProductViewBuilder {

    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final ProductMapper mapper;

    public ProductViewBuilder(CategoryRepository categories, ProductImageRepository images, ProductMapper mapper) {
        this.categories = categories;
        this.images = images;
        this.mapper = mapper;
    }

    public ProductResponse build(Product p) {
        String categoryName = categories.findById(p.getCategoryId()).map(Category::getName).orElse(null);
        return mapper.toResponse(p, categoryName, images.findByProductIdOrderBySortOrderAscCreatedAtAsc(p.getId()));
    }
}
