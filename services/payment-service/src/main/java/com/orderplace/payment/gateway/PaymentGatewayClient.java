package com.orderplace.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;

/** The only place that knows about the gateway. Swapping Razorpay for Stripe means writing another implementation. */
public interface PaymentGatewayClient {

    GatewayOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes);

    /** The public (test) key id the browser passes to Checkout. The secret never leaves the server. */
    String keyId();
}
