package com.orderplace.order.config;

import org.apache.kafka.common.errors.SerializationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry a failing event 5 times, one second apart (a version conflict or a short database outage usually clears),
 * then log it and move on so one bad event cannot block the topic. Unreadable events are never retried.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1000L, 5L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class, SerializationException.class);
        return handler;
    }
}
