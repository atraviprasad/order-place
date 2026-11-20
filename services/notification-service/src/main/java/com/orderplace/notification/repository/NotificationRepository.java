package com.orderplace.notification.repository;

import com.orderplace.notification.model.Notification;
import com.orderplace.notification.model.RecipientRole;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    Optional<Notification> findByEventIdAndRecipientRole(String eventId, RecipientRole recipientRole);
}
