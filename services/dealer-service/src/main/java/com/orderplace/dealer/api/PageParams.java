package com.orderplace.dealer.api;

import com.orderplace.common.web.ApiException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds a safe Pageable: size is capped at 100 and only whitelisted sort fields are accepted. */
final class PageParams {
    private static final int MAX_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("name", "dealerCode", "createdAt");

    private PageParams() {}

    static Pageable of(int page, int size, String sort) {
        if (page < 0 || size < 1) {
            throw ApiException.badRequest("page must be 0 or more and size must be 1 or more");
        }
        String[] parts = sort.split(",");
        String field = parts[0].strip();
        if (!SORTABLE.contains(field)) {
            throw ApiException.badRequest("Cannot sort by '" + field + "'. Allowed: " + SORTABLE);
        }
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].strip())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, Math.min(size, MAX_SIZE), Sort.by(dir, field));
    }
}
