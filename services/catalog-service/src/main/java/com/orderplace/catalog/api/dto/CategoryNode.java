package com.orderplace.catalog.api.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/** One node of the category tree. Serializable because the tree is cached in Redis. */
public record CategoryNode(UUID id, String name, int sortOrder, boolean active, List<CategoryNode> children)
        implements Serializable {}
