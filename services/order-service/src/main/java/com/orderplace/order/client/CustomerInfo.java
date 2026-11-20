package com.orderplace.order.client;

import java.util.UUID;

public record CustomerInfo(UUID id, String name, String email, String deliveryAddress, boolean active) {}
