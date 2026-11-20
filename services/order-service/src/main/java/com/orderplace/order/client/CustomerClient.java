package com.orderplace.order.client;

import com.orderplace.common.web.ApiException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public CustomerClient(@Value("${services.user-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** The calling customer's own organisation (the User Service reads org_id from the same token). */
    public CustomerInfo me() {
        try {
            Map<String, Object> body = http.get().uri("/customers/me")
                    .headers(h -> h.setBearerAuth(Downstream.bearerToken()))
                    .retrieve().body(MAP);
            if (body == null) throw Downstream.unavailable("User Service");
            return new CustomerInfo(UUID.fromString(String.valueOf(body.get("id"))),
                    Downstream.text(body.get("name")), Downstream.text(body.get("email")),
                    Downstream.text(body.get("deliveryAddress")), !Boolean.FALSE.equals(body.get("active")));
        } catch (HttpClientErrorException.Forbidden e) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No customer organisation is linked to this user");
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.unprocessable("ORGANISATION_NOT_FOUND", "Your customer organisation was not found");
        } catch (RestClientException e) {
            log.error("User Service call failed: {}", e.getMessage());
            throw Downstream.unavailable("User Service");
        }
    }
}
