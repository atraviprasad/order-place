package com.orderplace.user.client;

import com.orderplace.common.web.ApiException;
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
 * Asks the Dealer Service whether a dealer exists. Uses "token relay": the Admin's own bearer token is forwarded,
 * which is allowed because only Admins create users and GET /dealers/{id} is an Admin endpoint.
 */
@Component
public class DealerClient {

    private static final Logger log = LoggerFactory.getLogger(DealerClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public DealerClient(@Value("${services.dealer-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    public DealerInfo get(UUID dealerId) {
        try {
            Map<String, Object> body = http.get()
                    .uri("/dealers/{id}", dealerId)
                    .headers(h -> h.setBearerAuth(currentToken()))
                    .retrieve().body(MAP);
            if (body == null) {
                throw unavailable();
            }
            return new DealerInfo(dealerId, String.valueOf(body.get("name")), Boolean.TRUE.equals(body.get("active")));
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.unprocessable("ORGANISATION_NOT_FOUND", "Dealer not found");
        } catch (RestClientException e) {
            log.error("Dealer Service call failed: {}", e.getMessage());
            throw unavailable();
        }
    }

    private static String currentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No bearer token available to call the Dealer Service");
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "The Dealer Service is unavailable");
    }
}
