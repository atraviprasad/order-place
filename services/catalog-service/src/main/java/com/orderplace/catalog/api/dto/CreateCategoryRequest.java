package com.orderplace.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        UUID parentId,
        Integer sortOrder) {}
