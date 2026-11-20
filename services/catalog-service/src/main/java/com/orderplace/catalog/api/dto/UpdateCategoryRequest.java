package com.orderplace.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** parentId null means "top level". */
public record UpdateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        UUID parentId,
        Integer sortOrder,
        Long version) {}
