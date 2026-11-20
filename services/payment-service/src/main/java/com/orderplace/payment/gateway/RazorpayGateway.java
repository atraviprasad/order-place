package com.orderplace.payment.gateway;

import com.orderplace.common.web.ApiException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Creates Razorpay orders through the REST API (POST /v1/orders, HTTP Basic with key id and secret).
 * SANDBOX ONLY: the service refuses to start with a key that is not a test key.
 */
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "razorpay", matchIfMissing = true)
public class RazorpayGateway implements PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final String keyId;
    private final RestClient http;

    public RazorpayGateway(@Value("${razorpay.base-url}") String baseUrl,
                           @Value("${razorpay.key-id}") String keyId,
                           @Value("${razorpay.key-secret}") String keySecret) {
        requireTestKey(keyId);
        this.keyId = keyId;
        this.http = RestClient.builder().baseUrl(baseUrl)
                .defaultHeaders(h -> h.setBasicAuth(keyId, keySecret)).build();
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", toMinorUnits(amount));
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        try {
            Map<String, Object> response = http.post().uri("/orders").body(body).retrieve().body(MAP);
            if (response == null || response.get("id") == null) {
                throw unavailable();
            }
            return new GatewayOrder(String.valueOf(response.get("id")));
        } catch (RestClientException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw unavailable();
        }
    }

    @Override
    public String keyId() {
        return keyId;
    }

    /** 4500.00 INR becomes 450000 paise. Throws if the amount has more than two decimals. */
    static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    static void requireTestKey(String keyId) {
        if (keyId == null || !keyId.startsWith("rzp_test_")) {
            throw new IllegalStateException("This application is sandbox only: razorpay.key-id must be a test key (rzp_test_...)");
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "The payment gateway is unavailable. Try again later.");
    }
}
