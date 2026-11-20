package com.orderplace.catalog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/** Redis is only a speed-up. If it is down, log and read from the database instead of failing the request. */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override public void handleCacheGetError(RuntimeException e, Cache cache, Object key) { warn("get", cache, e); }
            @Override public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) { warn("put", cache, e); }
            @Override public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) { warn("evict", cache, e); }
            @Override public void handleCacheClearError(RuntimeException e, Cache cache) { warn("clear", cache, e); }

            private void warn(String op, Cache cache, RuntimeException e) {
                log.warn("Cache {} failed on '{}', continuing without cache: {}", op, cache.getName(), e.toString());
            }
        };
    }
}
