package com.orderplace.notification.rules;

import com.orderplace.common.events.EventTypes;
import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.notification.model.RecipientRole;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Who is told about which event, with which template (FR-NOT-02, FR-NOT-03). Unknown events are ignored. */
@Component
public class NotificationRules {

    public record Plan(RecipientRole recipient, String template) {}

    public Optional<Plan> planFor(String eventType) {
        if (eventType == null) return Optional.empty();
        return switch (eventType) {
            case EventTypes.ORDER_SUBMITTED -> plan(RecipientRole.DEALER, "OrderSubmitted");
            case EventTypes.ORDER_CANCELLED -> plan(RecipientRole.DEALER, "OrderCancelled");
            case EventTypes.ORDER_APPROVED -> plan(RecipientRole.CUSTOMER, "OrderApproved");
            case EventTypes.ORDER_EDITED -> plan(RecipientRole.CUSTOMER, "OrderEdited");
            case EventTypes.ORDER_REJECTED -> plan(RecipientRole.CUSTOMER, "OrderRejected");
            case EventTypes.ORDER_READY_FOR_DELIVERY -> plan(RecipientRole.CUSTOMER, "OrderReadyForDelivery");
            case EventTypes.PAYMENT_FAILED -> plan(RecipientRole.CUSTOMER, "PaymentFailed");
            default -> Optional.empty();
        };
    }

    public String subject(String eventType, OrderEventPayload p) {
        String number = p.orderNumber() == null ? "" : p.orderNumber();
        return switch (eventType) {
            case EventTypes.ORDER_SUBMITTED -> "New order " + number + " from " + p.customerName();
            case EventTypes.ORDER_CANCELLED -> "Order " + number + " was cancelled by " + p.customerName();
            case EventTypes.ORDER_APPROVED -> "Order " + number + " approved: payment is due";
            case EventTypes.ORDER_EDITED -> "Order " + number + " approved with changes: payment is due";
            case EventTypes.ORDER_REJECTED -> "Order " + number + " was rejected";
            case EventTypes.ORDER_READY_FOR_DELIVERY -> "Order " + number + " is ready for delivery";
            case EventTypes.PAYMENT_FAILED -> "Payment for order " + number + " failed";
            default -> "Order " + number + " update";
        };
    }

    private static Optional<Plan> plan(RecipientRole role, String template) {
        return Optional.of(new Plan(role, template));
    }
}
