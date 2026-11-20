package com.orderplace.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DEV ONLY stand-in for Razorpay (set payments.gateway=fake). Creates a made-up order id without any network call so
 * the whole flow, including signed webhooks sent with curl, can be tested without a Razorpay account or a tunnel.
 */
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "fake")
public class FakeGateway implements PaymentGatewayClient {

    private final String keyId;

    public FakeGateway(@Value("${razorpay.key-id}") String keyId) {
        RazorpayGateway.requireTestKey(keyId);
        this.keyId = keyId;
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes) {
        return new GatewayOrder("order_fake_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
    }

    @Override
    public String keyId() {
        return keyId;
    }
}
