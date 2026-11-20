package com.orderplace.order.client;

import com.orderplace.common.web.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DealerClient {

    private static final Logger log = LoggerFactory.getLogger(DealerClient.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public DealerClient(@Value("${services.dealer-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Uses the customer-visible list of ACTIVE dealers: a dealer missing from it is inactive or does not exist. */
    public DealerInfo requireActive(UUID dealerId) {
        try {
            List<Map<String, Object>> dealers = http.get().uri("/dealers/active")
                    .headers(h -> h.setBearerAuth(Downstream.bearerToken()))
                    .retrieve().body(LIST);
            if (dealers == null) throw Downstream.unavailable("Dealer Service");
            for (Map<String, Object> d : dealers) {
                if (dealerId.toString().equals(String.valueOf(d.get("id")))) {
                    return new DealerInfo(dealerId, Downstream.text(d.get("name")), Downstream.text(d.get("contactEmail")));
                }
            }
            throw ApiException.unprocessable("DEALER_INACTIVE", "The selected dealer is not active");
        } catch (RestClientException e) {
            log.error("Dealer Service call failed: {}", e.getMessage());
            throw Downstream.unavailable("Dealer Service");
        }
    }
}
