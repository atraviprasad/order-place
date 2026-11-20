package com.orderplace.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class SignatureVerifierTest {

    private static final String KEY_SECRET = "key-secret";
    private static final String WEBHOOK_SECRET = "webhook-secret";
    private final SignatureVerifier verifier = new SignatureVerifier(KEY_SECRET, WEBHOOK_SECRET);

    static String hmac(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }

    @Test
    void acceptsValidCheckoutSignature() throws Exception {
        String sig = hmac(KEY_SECRET, "order_1|pay_1".getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verifyCheckout("order_1", "pay_1", sig)).isTrue();
    }

    @Test
    void rejectsCheckoutSignatureForOtherPayment() throws Exception {
        String sig = hmac(KEY_SECRET, "order_1|pay_1".getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verifyCheckout("order_1", "pay_2", sig)).isFalse();
    }

    @Test
    void acceptsValidWebhookSignatureOverRawBytes() throws Exception {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verifyWebhook(body, hmac(WEBHOOK_SECRET, body))).isTrue();
    }

    @Test
    void rejectsWebhookWhenBodyChangedByOneByte() throws Exception {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String sig = hmac(WEBHOOK_SECRET, body);
        byte[] tampered = "{\"event\":\"payment.captureD\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verifyWebhook(tampered, sig)).isFalse();
    }

    @Test
    void rejectsMissingOrBlankSignature() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verifyWebhook(body, null)).isFalse();
        assertThat(verifier.verifyWebhook(body, "")).isFalse();
    }

    @Test
    void checkoutSignatureIsNotAcceptedAsWebhookSignature() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verifyWebhook(body, hmac(KEY_SECRET, body))).isFalse();
    }
}
