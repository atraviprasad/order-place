package com.orderplace.notification.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderplace.common.events.EventTypes;
import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.notification.model.RecipientRole;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationRulesTest {

    private final NotificationRules rules = new NotificationRules();

    private OrderEventPayload payload() {
        return new OrderEventPayload(UUID.randomUUID(), "OP-2026-000007", UUID.randomUUID(), "Demo Customer",
                UUID.randomUUID(), "c@x.com", "d@x.com", new BigDecimal("1200.00"), "INR", null);
    }

    @Test
    void dealerIsToldAboutSubmissionAndCancellation() {
        assertThat(rules.planFor(EventTypes.ORDER_SUBMITTED)).hasValueSatisfying(p -> {
            assertThat(p.recipient()).isEqualTo(RecipientRole.DEALER);
            assertThat(p.template()).isEqualTo("OrderSubmitted");
        });
        assertThat(rules.planFor(EventTypes.ORDER_CANCELLED).orElseThrow().recipient()).isEqualTo(RecipientRole.DEALER);
    }

    @Test
    void customerIsToldAboutDecisionsAndDelivery() {
        for (String type : new String[] {EventTypes.ORDER_APPROVED, EventTypes.ORDER_EDITED,
                EventTypes.ORDER_REJECTED, EventTypes.ORDER_READY_FOR_DELIVERY}) {
            assertThat(rules.planFor(type).orElseThrow().recipient()).as(type).isEqualTo(RecipientRole.CUSTOMER);
        }
    }

    @Test
    void unknownOrMissingEventTypesAreIgnored() {
        assertThat(rules.planFor("SomethingElse")).isEmpty();
        assertThat(rules.planFor(null)).isEmpty();
        assertThat(rules.planFor(EventTypes.PAYMENT_SUCCEEDED)).isEmpty();   // not an order event, handled later
    }

    @Test
    void subjectsContainTheOrderNumber() {
        for (String type : new String[] {EventTypes.ORDER_SUBMITTED, EventTypes.ORDER_CANCELLED, EventTypes.ORDER_APPROVED,
                EventTypes.ORDER_EDITED, EventTypes.ORDER_REJECTED, EventTypes.ORDER_READY_FOR_DELIVERY}) {
            assertThat(rules.subject(type, payload())).as(type).contains("OP-2026-000007");
        }
        assertThat(rules.subject(EventTypes.ORDER_SUBMITTED, payload())).contains("Demo Customer");
    }
}
