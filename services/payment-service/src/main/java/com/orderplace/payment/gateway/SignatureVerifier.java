package com.orderplace.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 checks used by Razorpay.
 * Checkout result: HMAC(keySecret, razorpay_order_id + "|" + razorpay_payment_id)
 * Webhook:         HMAC(webhookSecret, raw request body)
 * Comparison is constant-time so the signature cannot be guessed byte by byte.
 */
@Component
public class SignatureVerifier {

    private final String keySecret;
    private final String webhookSecret;

    public SignatureVerifier(@Value("${razorpay.key-secret}") String keySecret,
                             @Value("${razorpay.webhook-secret}") String webhookSecret) {
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    public boolean verifyCheckout(String gatewayOrderId, String gatewayPaymentId, String signature) {
        if (gatewayOrderId == null || gatewayPaymentId == null) return false;
        return matches(hmacHex(keySecret, (gatewayOrderId + "|" + gatewayPaymentId).getBytes(StandardCharsets.UTF_8)), signature);
    }

    public boolean verifyWebhook(byte[] rawBody, String signature) {
        if (rawBody == null) return false;
        return matches(hmacHex(webhookSecret, rawBody), signature);
    }

    static String hmacHex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static boolean matches(String expectedHex, String givenSignature) {
        if (givenSignature == null || givenSignature.isBlank()) return false;
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
                givenSignature.strip().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
