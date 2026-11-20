package com.orderplace.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Imported by services via @Import so the filter is registered. */
@Configuration
public class CommonWebConfig {
    @Bean
    CorrelationIdFilter correlationIdFilter() { return new CorrelationIdFilter(); }

    @Bean
    GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
}
