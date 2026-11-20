package com.orderplace.payment.gateway;

/** The order the gateway created for one payment (Razorpay: order_xxxx). */
public record GatewayOrder(String id) {}
