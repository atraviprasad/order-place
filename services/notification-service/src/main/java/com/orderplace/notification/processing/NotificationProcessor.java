package com.orderplace.notification.processing;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.notification.mail.EmailRenderer;
import com.orderplace.notification.mail.EmailSender;
import com.orderplace.notification.model.Notification;
import com.orderplace.notification.model.NotificationStatus;
import com.orderplace.notification.repository.NotificationRepository;
import com.orderplace.notification.rules.NotificationRules;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

/**
 * Turns one order event into one logged, sent email.
 * Idempotent: the log row (unique per event and recipient) is created first. If the event arrives again, a SENT or
 * FAILED row means "nothing to do", a PENDING row means "the earlier attempt did not finish, try again".
 * Retries: a failed send is recorded and the exception is rethrown so Kafka redelivers the event after a back-off.
 * After the last attempt the row is marked FAILED and the event is considered handled.
 */
@Component
public class NotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessor.class);

    private final NotificationRules rules;
    private final NotificationRepository repository;
    private final EmailRenderer renderer;
    private final EmailSender sender;

    @Value("${notifications.max-attempts:4}")
    private int maxAttempts = 4;

    public NotificationProcessor(NotificationRules rules, NotificationRepository repository,
                                 EmailRenderer renderer, EmailSender sender) {
        this.rules = rules;
        this.repository = repository;
        this.renderer = renderer;
        this.sender = sender;
    }

    public void handle(EventEnvelope<OrderEventPayload> event) {
        Optional<NotificationRules.Plan> plan = rules.planFor(event.eventType());
        if (plan.isEmpty()) {
            log.debug("No notification for event type {}", event.eventType());
            return;
        }
        OrderEventPayload payload = event.payload();
        String recipient = switch (plan.get().recipient()) {
            case DEALER -> payload.dealerEmail();
            case CUSTOMER -> payload.customerEmail();
        };

        Notification n = findOrCreate(event, plan.get(), recipient);
        if (n.getStatus() != NotificationStatus.PENDING) {
            log.info("Event {} already handled for {} ({}), skipping", event.eventId(), n.getRecipientRole(), n.getStatus());
            return;
        }
        if (recipient == null || recipient.isBlank()) {
            n.markFailed("No recipient email address on the event");
            repository.save(n);
            log.warn("Notification for event {} has no recipient email", event.eventId());
            return;
        }
        if (n.getBody() == null) {
            n.prepare(rules.subject(event.eventType(), payload),
                    renderer.render(plan.get().template(), plan.get().recipient(), payload));
        }
        try {
            sender.send(n.getRecipient(), n.getSubject(), n.getBody());
            n.markSent();
            repository.save(n);
        } catch (MailException e) {
            n.recordFailure(e.getMessage());
            if (n.getAttempts() >= maxAttempts) {
                n.markFailed(e.getMessage());
                repository.save(n);
                log.error("Giving up on notification for event {} after {} attempts: {}", event.eventId(), n.getAttempts(), e.getMessage());
                return;
            }
            repository.save(n);
            log.warn("Sending email for event {} failed (attempt {}), will retry: {}", event.eventId(), n.getAttempts(), e.getMessage());
            throw e;
        }
    }

    private Notification findOrCreate(EventEnvelope<OrderEventPayload> event, NotificationRules.Plan plan, String recipient) {
        String eventId = event.eventId().toString();
        Optional<Notification> existing = repository.findByEventIdAndRecipientRole(eventId, plan.recipient());
        if (existing.isPresent()) return existing.get();
        OrderEventPayload p = event.payload();
        Notification created = Notification.create(eventId, event.eventType(), String.valueOf(p.orderId()),
                p.orderNumber(), plan.recipient(), recipient, event.correlationId());
        try {
            return repository.insert(created);
        } catch (DuplicateKeyException e) {
            // another consumer thread created it between our check and insert: use theirs
            return repository.findByEventIdAndRecipientRole(eventId, plan.recipient()).orElseThrow(() -> e);
        }
    }
}
