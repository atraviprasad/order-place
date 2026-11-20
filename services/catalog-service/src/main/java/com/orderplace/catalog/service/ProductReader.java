package com.orderplace.catalog.service;

import com.orderplace.catalog.api.dto.ProductResponse;
import com.orderplace.common.web.ApiException;
import com.orderplace.catalog.repository.ProductRepository;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cached read of one product (active or not). Visibility rules are applied by the caller, after the cache,
 * so one cache entry serves everybody. This is a separate bean so the @Cacheable proxy is actually used.
 */
@Component
public class ProductReader {

    private final ProductRepository products;
    private final ProductViewBuilder builder;

    public ProductReader(ProductRepository products, ProductViewBuilder builder) {
        this.products = products;
        this.builder = builder;
    }

    @Cacheable(cacheNames = CatalogCache.PRODUCT, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse load(UUID id) {
        return builder.build(products.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id)));
    }
}
