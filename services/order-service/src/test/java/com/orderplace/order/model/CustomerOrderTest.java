package com.orderplace.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderplace.common.web.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CustomerOrderTest {

    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID p3 = UUID.randomUUID();

    private static LineSpec spec(UUID product, String price, int qty) {
        return new LineSpec(product, "SKU-" + qty, "Product", new BigDecimal(price), qty);
    }

    private CustomerOrder draft() {
        return CustomerOrder.newDraft(UUID.randomUUID(), "Customer", "c@x.com", UUID.randomUUID(), "Address");
    }

    private static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void newDraftStartsEmpty() {
        CustomerOrder o = draft();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(o.getLines()).isEmpty();
        assertThat(o.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(o.getCurrency()).isEqualTo("INR");
        assertThat(o.getOrderNumber()).isNull();
    }

    @Test
    void saveDraftCalculatesLineTotalsAndOrderTotal() {
        CustomerOrder o = draft();
        o.saveDraft(UUID.randomUUID(), "Dealer", "d@x.com", "Addr", "notes",
                List.of(spec(p1, "4500.00", 10), spec(p2, "100.50", 3)));

        assertThat(o.getLines()).hasSize(2);
        assertThat(o.getLines().get(0).getLineTotal()).isEqualByComparingTo("45000.00");
        assertThat(o.getLines().get(1).getLineTotal()).isEqualByComparingTo("301.50");
        assertThat(o.getSubtotal()).isEqualByComparingTo("45301.50");
        assertThat(o.getTaxAmount()).isEqualByComparingTo("0");
        assertThat(o.getTotalAmount()).isEqualByComparingTo("45301.50");
        assertThat(o.getDealerName()).isEqualTo("Dealer");
    }

    @Test
    void saveDraftAgainUpdatesInPlaceRemovesAndAdds() {
        CustomerOrder o = draft();
        o.saveDraft(null, null, null, "Addr", null, List.of(spec(p1, "100.00", 1), spec(p2, "200.00", 2)));
        OrderLine firstLine = o.getLines().get(0);

        o.saveDraft(null, null, null, "Addr", null, List.of(spec(p1, "100.00", 5), spec(p3, "50.00", 4)));

        assertThat(o.getLines()).extracting(OrderLine::getProductId).containsExactlyInAnyOrder(p1, p3);
        assertThat(o.getLines()).contains(firstLine);                 // same object: updated, not replaced
        assertThat(firstLine.getSubmittedQty()).isEqualTo(5);
        assertThat(o.getTotalAmount()).isEqualByComparingTo("700.00"); // 5*100 + 4*50
    }

    @Test
    void saveDraftWithNoLinesEmptiesTheOrder() {
        CustomerOrder o = draft();
        o.saveDraft(null, null, null, "Addr", null, List.of(spec(p1, "100.00", 1)));
        o.saveDraft(null, null, null, "Addr", null, List.of());

        assertThat(o.getLines()).isEmpty();
        assertThat(o.getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void submitFreezesTheDraft() {
        CustomerOrder o = draft();
        o.saveDraft(UUID.randomUUID(), "Dealer", null, "Addr", null, List.of(spec(p1, "100.00", 1)));

        o.submit("OP-2026-000001", "key-1", Instant.now());

        assertThat(o.getStatus()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(o.getOrderNumber()).isEqualTo("OP-2026-000001");
        assertThat(o.getSubmitIdempotencyKey()).isEqualTo("key-1");
        assertThat(o.getSubmittedAt()).isNotNull();
        assertThatThrownBy(() -> o.saveDraft(null, null, null, "x", null, List.of()))
                .satisfies(t -> assertCode(t, "ORDER_NOT_EDITABLE"));
        assertThatThrownBy(o::requireDraft).satisfies(t -> assertCode(t, "ORDER_NOT_EDITABLE"));
    }

    @Test
    void cannotSubmitTwice() {
        CustomerOrder o = draft();
        o.submit("OP-2026-000001", "k", Instant.now());

        assertThatThrownBy(() -> o.submit("OP-2026-000002", "k2", Instant.now()))
                .satisfies(t -> assertCode(t, "INVALID_STATE_TRANSITION"));
    }

    @Test
    void draftCannotBeCancelledButSubmittedCan() {
        CustomerOrder o = draft();
        assertThatThrownBy(o::cancel).satisfies(t -> assertCode(t, "INVALID_STATE_TRANSITION"));

        o.submit("OP-2026-000001", "k", Instant.now());
        o.cancel();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(o::cancel).satisfies(t -> assertCode(t, "INVALID_STATE_TRANSITION"));
    }
}
