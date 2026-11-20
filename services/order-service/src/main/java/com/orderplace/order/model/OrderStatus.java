package com.orderplace.order.model;

/** The order life cycle. All transition rules live here, in one place. */
public enum OrderStatus {
    DRAFT, SUBMITTED, UNDER_REVIEW, AWAITING_PAYMENT, PAID, READY_FOR_DELIVERY, REJECTED, CANCELLED;

    public boolean canGoTo(OrderStatus next) {
        return switch (this) {
            case DRAFT -> next == SUBMITTED;
            case SUBMITTED -> next == UNDER_REVIEW || next == CANCELLED;
            case UNDER_REVIEW -> next == AWAITING_PAYMENT || next == REJECTED || next == CANCELLED;
            case AWAITING_PAYMENT -> next == PAID || next == CANCELLED;
            case PAID -> next == READY_FOR_DELIVERY;
            case READY_FOR_DELIVERY, REJECTED, CANCELLED -> false;
        };
    }
}
