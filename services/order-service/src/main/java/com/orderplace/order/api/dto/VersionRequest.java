package com.orderplace.order.api.dto;

/** Body for actions that need nothing but the version the user saw. The whole body is optional. */
public record VersionRequest(Long version) {}
