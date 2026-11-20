package com.orderplace.payment.service;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Test helper: signs like Razorpay does. */
final class SignatureVerifierTestSupport {

    private SignatureVerifierTestSupport() {}

    static String hmac(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String checkoutSig(String keySecret, String orderId, String paymentId) {
        return hmac(keySecret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
    }
}
