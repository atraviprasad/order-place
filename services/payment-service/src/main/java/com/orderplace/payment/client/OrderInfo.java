package com.orderplace.payment.client;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderInfo(UUID id, String orderNumber, String status, UUID customerId, BigDecimal totalAmount, String currency) {}
