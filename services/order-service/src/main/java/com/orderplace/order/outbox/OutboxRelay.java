package com.orderplace.order.outbox;

import com.orderplace.order.model.OutboxEvent;
import com.orderplace.order.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox rows to Kafka, oldest first. Delivery is "at least once": if the service stops after Kafka
 * accepted a message but before the row is marked, the event is sent again, so consumers must ignore duplicates
 * (they can use the eventId). On a failure the batch stops so the order of events is kept.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    @Value("${orders.outbox.batch-size:50}")
    private int batchSize = 50;

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${orders.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findUnpublished(PageRequest.of(0, batchSize));
        for (OutboxEvent event : pending) {
            try {
                kafka.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload()).get(10, TimeUnit.SECONDS);
                outbox.markPublished(event.getId(), Instant.now());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                String message = String.valueOf(e.getMessage());
                log.warn("Could not publish outbox event {} ({}): {}", event.getId(), event.getEventType(), message);
                outbox.markFailed(event.getId(), message.length() > 480 ? message.substring(0, 480) : message);
                return;
            }
        }
    }
}
