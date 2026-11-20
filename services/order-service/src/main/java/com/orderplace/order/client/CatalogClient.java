package com.orderplace.order.client;

import com.orderplace.common.web.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public CatalogClient(@Value("${services.catalog-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** The Catalog hides inactive products from non-admins (404), so "not found" means "not orderable". */
    public ProductInfo get(UUID productId) {
        try {
            Map<String, Object> body = http.get().uri("/products/{id}", productId)
                    .headers(h -> h.setBearerAuth(Downstream.bearerToken()))
                    .retrieve().body(MAP);
            if (body == null) throw Downstream.unavailable("Catalog Service");
            return new ProductInfo(productId,
                    Downstream.text(body.get("sku")),
                    Downstream.text(body.get("name")),
                    new BigDecimal(String.valueOf(body.get("unitPrice"))).setScale(2, java.math.RoundingMode.HALF_UP),
                    "AVAILABLE".equals(body.get("availability")),
                    body.get("minOrderQty") instanceof Number n ? n.intValue() : 1,
                    !Boolean.FALSE.equals(body.get("active")));
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.unprocessable("PRODUCT_UNAVAILABLE", "Product " + productId + " is not available");
        } catch (RestClientException e) {
            log.error("Catalog Service call failed: {}", e.getMessage());
            throw Downstream.unavailable("Catalog Service");
        }
    }
}
