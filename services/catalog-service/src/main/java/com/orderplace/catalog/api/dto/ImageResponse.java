package com.orderplace.catalog.api.dto;

import java.io.Serializable;
import java.util.UUID;

public record ImageResponse(UUID id, String url, boolean primary, int sortOrder) implements Serializable {}
