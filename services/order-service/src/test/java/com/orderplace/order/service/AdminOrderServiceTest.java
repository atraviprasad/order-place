package com.orderplace.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.orderplace.order.api.dto.OrderStatusSummary;
import com.orderplace.order.model.OrderStatus;
import com.orderplace.order.repository.OrderRepository;
import com.orderplace.order.repository.OrderStatusHistoryRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository history;
    @Mock com.orderplace.order.repository.DealerActionRepository actions;
    @InjectMocks AdminOrderService service;

    @Test
    void summary_fillsMissingStatusesWithZeroAndIgnoresDrafts() {
        when(orders.countByStatus()).thenReturn(Arrays.asList(new Object[][] {
                {OrderStatus.SUBMITTED, 3L}, {OrderStatus.CANCELLED, 1L}, {OrderStatus.DRAFT, 9L}}));

        OrderStatusSummary summary = service.summary();

        assertThat(summary.counts()).doesNotContainKey(OrderStatus.DRAFT);
        assertThat(summary.counts().get(OrderStatus.SUBMITTED)).isEqualTo(3L);
        assertThat(summary.counts().get(OrderStatus.UNDER_REVIEW)).isZero();
        assertThat(summary.counts().get(OrderStatus.READY_FOR_DELIVERY)).isZero();
        assertThat(summary.total()).isEqualTo(4L);
    }
}
