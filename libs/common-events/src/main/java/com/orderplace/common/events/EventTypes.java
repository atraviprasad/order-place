package com.orderplace.common.events;

public final class EventTypes {
    public static final String ORDER_SUBMITTED = "OrderSubmitted";
    public static final String ORDER_APPROVED = "OrderApproved";
    public static final String ORDER_EDITED = "OrderEdited";
    public static final String ORDER_REJECTED = "OrderRejected";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String ORDER_READY_FOR_DELIVERY = "OrderReadyForDelivery";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    private EventTypes() {}
}
