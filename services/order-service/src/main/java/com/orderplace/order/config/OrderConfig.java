package com.orderplace.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Auditing fills created_at / updated_at; scheduling runs the outbox relay. */
@Configuration
@EnableJpaAuditing
@EnableScheduling
public class OrderConfig {}
