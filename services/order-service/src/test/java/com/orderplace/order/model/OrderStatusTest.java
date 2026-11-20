package com.orderplace.order.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void allowedTransitions() {
        assertThat(OrderStatus.DRAFT.canGoTo(OrderStatus.SUBMITTED)).isTrue();
        assertThat(OrderStatus.SUBMITTED.canGoTo(OrderStatus.UNDER_REVIEW)).isTrue();
        assertThat(OrderStatus.SUBMITTED.canGoTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.UNDER_REVIEW.canGoTo(OrderStatus.AWAITING_PAYMENT)).isTrue();
        assertThat(OrderStatus.UNDER_REVIEW.canGoTo(OrderStatus.REJECTED)).isTrue();
        assertThat(OrderStatus.UNDER_REVIEW.canGoTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.AWAITING_PAYMENT.canGoTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.AWAITING_PAYMENT.canGoTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PAID.canGoTo(OrderStatus.READY_FOR_DELIVERY)).isTrue();
    }

    @Test
    void forbiddenTransitions() {
        assertThat(OrderStatus.DRAFT.canGoTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.DRAFT.canGoTo(OrderStatus.AWAITING_PAYMENT)).isFalse();
        assertThat(OrderStatus.SUBMITTED.canGoTo(OrderStatus.AWAITING_PAYMENT)).isFalse();
        assertThat(OrderStatus.SUBMITTED.canGoTo(OrderStatus.REJECTED)).isFalse();
        assertThat(OrderStatus.AWAITING_PAYMENT.canGoTo(OrderStatus.REJECTED)).isFalse();
        assertThat(OrderStatus.PAID.canGoTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void finalStatesGoNowhere() {
        for (OrderStatus end : new OrderStatus[] {OrderStatus.READY_FOR_DELIVERY, OrderStatus.REJECTED, OrderStatus.CANCELLED}) {
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(end.canGoTo(next)).as(end + " -> " + next).isFalse();
            }
        }
    }
}
