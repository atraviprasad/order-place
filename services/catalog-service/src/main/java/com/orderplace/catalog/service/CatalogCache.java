package com.orderplace.catalog.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Evicts cache entries only AFTER the database transaction has committed. Evicting earlier would let a concurrent
 * reader put the old value back into Redis. A Redis failure is logged and never fails the request.
 */
@Component
public class CatalogCache {

    public static final String PRODUCT = "product";
    public static final String CATEGORY_TREE = "categoryTree";

    private static final Logger log = LoggerFactory.getLogger(CatalogCache.class);
    private final CacheManager manager;

    public CatalogCache(CacheManager manager) {
        this.manager = manager;
    }

    public void evictProduct(UUID id) {
        AfterCommit.run(() -> evict(PRODUCT, id));
    }

    /** Needed when a category is renamed: every cached product carries its category name. */
    public void evictAllProducts() {
        AfterCommit.run(() -> clear(PRODUCT));
    }

    public void evictCategoryTree() {
        AfterCommit.run(() -> clear(CATEGORY_TREE));
    }

    private void evict(String name, Object key) {
        try {
            Cache cache = manager.getCache(name);
            if (cache != null) cache.evict(key);
        } catch (RuntimeException e) {
            log.warn("Could not evict {}/{} from cache: {}", name, key, e.toString());
        }
    }

    private void clear(String name) {
        try {
            Cache cache = manager.getCache(name);
            if (cache != null) cache.clear();
        } catch (RuntimeException e) {
            log.warn("Could not clear cache {}: {}", name, e.toString());
        }
    }
}
