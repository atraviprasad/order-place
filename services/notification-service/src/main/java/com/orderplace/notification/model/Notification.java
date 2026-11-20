package com.orderplace.notification.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One row of the notification log. The unique (eventId, recipientRole) index is what makes processing idempotent:
 * the same event can be delivered by Kafka twice, but only one notification per recipient can exist for it.
 * Ids are stored as plain strings (no UUID representation settings needed in MongoDB).
 */
@Document("notifications")
@CompoundIndex(name = "uq_event_recipient", def = "{'eventId': 1, 'recipientRole': 1}", unique = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    private String id;

    private String eventId;
    @Indexed
    private String eventType;
    private String orderId;
    private String orderNumber;
    private RecipientRole recipientRole;
    @Indexed
    private String recipient;
    private String channel = "EMAIL";
    private String subject;
    private String body;
    @Indexed
    private NotificationStatus status = NotificationStatus.PENDING;
    private int attempts;
    private String lastError;
    @Indexed
    private Instant createdAt;
    private Instant sentAt;
    private String correlationId;

    public static Notification create(String eventId, String eventType, String orderId, String orderNumber,
                                      RecipientRole role, String recipient, String correlationId) {
        Notification n = new Notification();
        n.eventId = eventId;
        n.eventType = eventType;
        n.orderId = orderId;
        n.orderNumber = orderNumber;
        n.recipientRole = role;
        n.recipient = recipient;
        n.correlationId = correlationId;
        n.createdAt = Instant.now();
        return n;
    }

    /** The email content is stored with the log so a failed one can be re-sent exactly as it was. */
    public void prepare(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.attempts++;
        this.sentAt = Instant.now();
        this.lastError = null;
    }

    /** One failed attempt. The status does not change: the caller decides whether to keep trying. */
    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = truncate(error);
    }

    public void markFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.lastError = truncate(error);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
