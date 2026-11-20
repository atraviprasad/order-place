package com.orderplace.notification.admin;

import com.orderplace.notification.model.Notification;
import com.orderplace.notification.model.NotificationStatus;
import com.orderplace.notification.model.RecipientRole;
import java.time.Instant;

public record NotificationResponse(String id, String eventType, String orderId, String orderNumber,
                                   RecipientRole recipientRole, String recipient, String channel, String subject,
                                   NotificationStatus status, int attempts, String lastError,
                                   Instant createdAt, Instant sentAt) {

    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventType(), n.getOrderId(), n.getOrderNumber(),
                n.getRecipientRole(), n.getRecipient(), n.getChannel(), n.getSubject(), n.getStatus(),
                n.getAttempts(), n.getLastError(), n.getCreatedAt(), n.getSentAt());
    }
}
