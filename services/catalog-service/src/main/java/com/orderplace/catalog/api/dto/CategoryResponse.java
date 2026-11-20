package com.orderplace.catalog.api.dto;

import java.util.UUID;

public record CategoryResponse(UUID id, UUID parentId, String name, int sortOrder, boolean active, Long version) {}
