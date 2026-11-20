package com.orderplace.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderplace.common.web.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CustomerOrderApprovalTest {

    private final UUID line1 = UUID.randomUUID();

    private CustomerOrder submitted() {
        CustomerOrder o = CustomerOrder.newDraft(UUID.randomUUID(), "Customer", "c@x.com", UUID.randomUUID(), "Address");
        o.saveDraft(UUID.randomUUID(), "Dealer", null, "Address", null,
                List.of(new LineSpec(UUID.randomUUID(), "CHR-1", "Chair", new BigDecimal("100.00"), 10)));
        ReflectionTestUtils.setField(o.getLines().get(0), "id", line1);
        o.submit("OP-2026-000001", "k", Instant.now());
        return o;
    }

    @Test
    void approveKeepsSubmittedQuantityAndPrice() {
        CustomerOrder o = submitted();
        o.startReview();

        o.approve(Map.of(line1, 6));

        OrderLine l = o.getLines().get(0);
        assertThat(l.getSubmittedQty()).isEqualTo(10);
        assertThat(l.getApprovedQty()).isEqualTo(6);
        assertThat(l.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(l.getLineTotal()).isEqualByComparingTo("600.00");
        assertThat(o.getTotalAmount()).isEqualByComparingTo("600.00");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void approveFromTheWrongStateChangesNothing() {
        CustomerOrder o = submitted();   // still SUBMITTED, review not started

        assertThatThrownBy(() -> o.approve(Map.of(line1, 6))).isInstanceOf(ApiException.class);
        assertThat(o.getLines().get(0).getApprovedQty()).isNull();
        assertThat(o.getTotalAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectOnlyFromUnderReview() {
        CustomerOrder o = submitted();
        assertThatThrownBy(o::reject).isInstanceOf(ApiException.class);

        o.startReview();
        o.reject();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void customerCanStillCancelAfterApproval() {
        CustomerOrder o = submitted();
        o.startReview();
        o.approve(Map.of());

        o.cancel();

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
