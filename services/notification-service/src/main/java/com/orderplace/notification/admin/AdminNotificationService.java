package com.orderplace.notification.admin;

import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.notification.mail.EmailSender;
import com.orderplace.notification.model.Notification;
import com.orderplace.notification.model.NotificationStatus;
import com.orderplace.notification.repository.NotificationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
public class AdminNotificationService {

    private final MongoTemplate mongo;
    private final NotificationRepository repository;
    private final EmailSender sender;

    public AdminNotificationService(MongoTemplate mongo, NotificationRepository repository, EmailSender sender) {
        this.mongo = mongo;
        this.repository = repository;
        this.sender = sender;
    }

    public PagedResponse<NotificationResponse> list(String eventType, NotificationStatus status, String recipient,
                                                    LocalDate from, LocalDate to, Pageable pageable) {
        List<Criteria> conditions = new ArrayList<>();
        if (eventType != null && !eventType.isBlank()) conditions.add(Criteria.where("eventType").is(eventType.strip()));
        if (status != null) conditions.add(Criteria.where("status").is(status));
        if (recipient != null && !recipient.isBlank()) {
            conditions.add(Criteria.where("recipient").regex(Pattern.quote(recipient.strip()), "i"));
        }
        if (from != null) conditions.add(Criteria.where("createdAt").gte(from.atStartOfDay().toInstant(ZoneOffset.UTC)));
        if (to != null) conditions.add(Criteria.where("createdAt").lt(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));

        Query base = new Query();
        if (!conditions.isEmpty()) base.addCriteria(new Criteria().andOperator(conditions));
        long total = mongo.count(Query.of(base), Notification.class);
        List<NotificationResponse> rows = mongo.find(Query.of(base).with(pageable), Notification.class)
                .stream().map(NotificationResponse::of).toList();
        int pages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PagedResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), total, pages);
    }

    /** Re-sends a FAILED notification exactly as it was stored. If it fails again it stays FAILED with the new error. */
    public NotificationResponse retry(String id) {
        Notification n = repository.findById(id).orElseThrow(() -> ApiException.notFound("Notification not found: " + id));
        if (n.getStatus() != NotificationStatus.FAILED) {
            throw ApiException.conflict("NOT_RETRYABLE", "Only failed notifications can be retried (this one is " + n.getStatus() + ")");
        }
        if (n.getRecipient() == null || n.getRecipient().isBlank() || n.getBody() == null) {
            throw ApiException.unprocessable("NOT_RETRYABLE", "This notification has no recipient or content to send");
        }
        try {
            sender.send(n.getRecipient(), n.getSubject(), n.getBody());
            n.markSent();
        } catch (MailException e) {
            n.recordFailure(e.getMessage());
        }
        return NotificationResponse.of(repository.save(n));
    }
}
