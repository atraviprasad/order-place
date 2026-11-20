package com.orderplace.common.web;

import java.util.List;

/** Paged response shape from the API design. Build it from a Spring Data Page in each service: new PagedResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages()). */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
