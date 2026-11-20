package com.orderplace.payment.client;

import com.orderplace.common.web.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the order with the customer's own token ("token relay"). The Order Service only returns orders of the
 * caller's organisation, so this also proves the order belongs to the person paying. The amount comes from here and
 * never from the browser.
 */
@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public OrderClient(@Value("${services.order-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    public OrderInfo get(UUID orderId) {
        try {
            Map<String, Object> body = http.get().uri("/orders/{id}", orderId)
                    .headers(h -> h.setBearerAuth(currentToken()))
                    .retrieve().body(MAP);
            if (body == null) throw unavailable();
            return new OrderInfo(orderId, text(body.get("orderNumber")), text(body.get("status")),
                    body.get("customerId") == null ? null : UUID.fromString(String.valueOf(body.get("customerId"))),
                    new BigDecimal(String.valueOf(body.get("totalAmount"))), text(body.get("currency")));
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Order not found: " + orderId);
        } catch (RestClientException e) {
            log.error("Order Service call failed: {}", e.getMessage());
            throw unavailable();
        }
    }

    private static String currentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) return jwt.getToken().getTokenValue();
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No bearer token available to call the Order Service");
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "The Order Service is unavailable");
    }
}
