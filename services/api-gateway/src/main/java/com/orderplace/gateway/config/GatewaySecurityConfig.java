package com.orderplace.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/** Validates the JWT at the edge. The Razorpay webhook is public (signature is verified in the Payment Service). */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(a -> a
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers("/api/v1/webhooks/**").permitAll()
                        .pathMatchers("/internal/**", "/api/v1/internal/**").denyAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> {}))
                .build();
    }
}
