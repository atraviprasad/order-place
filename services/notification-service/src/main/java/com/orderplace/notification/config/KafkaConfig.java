package com.orderplace.notification.config;

import com.orderplace.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Retry and dead-letter behaviour of the listener.
 * - A failed email send is retried with a doubling back-off (the processor decides when to stop).
 * - After the retries, or for a message that can never be processed (bad JSON), the message goes to notification-dlt
 *   so it is kept for inspection instead of blocking the topic.
 */
@Configuration
public class KafkaConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template,
                                          @Value("${notifications.max-attempts:4}") int maxAttempts,
                                          @Value("${notifications.retry-initial-ms:2000}") long initialMs) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(Topics.NOTIFICATION_DLT, -1));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(Math.max(0, maxAttempts - 1));
        backOff.setInitialInterval(initialMs);
        backOff.setMultiplier(2.0);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);          // unreadable message: straight to DLT
        return handler;
    }

    @Bean
    NewTopic notificationDeadLetterTopic() {
        return TopicBuilder.name(Topics.NOTIFICATION_DLT).partitions(1).replicas(1).build();
    }
}
