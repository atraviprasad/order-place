package com.orderplace.payment.service;

import com.orderplace.common.events.EventTypes;
import com.orderplace.common.web.ApiException;
import com.orderplace.payment.model.Payment;
import com.orderplace.payment.model.PaymentEvent;
import com.orderplace.payment.model.PaymentStatus;
import com.orderplace.payment.model.ProcessingResult;
import com.orderplace.payment.gateway.SignatureVerifier;
import com.orderplace.payment.outbox.OutboxWriter;
import com.orderplace.payment.repository.PaymentEventRepository;
import com.orderplace.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only way a payment becomes SUCCESS. Rules:
 * 1. The signature is checked against the raw bytes first; a bad one is rejected (400) and nothing is stored.
 * 2. Each gateway event id is processed once; a repeat is acknowledged and ignored.
 * 3. SUCCESS is final; a later failure never undoes it; a success may follow an earlier failure (customer retried).
 * 4. An amount that differs from our payment, or a second success for the same order, is recorded but not applied.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final SignatureVerifier signatures;
    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final OutboxWriter outbox;

    public WebhookService(SignatureVerifier signatures, PaymentRepository payments, PaymentEventRepository events,
                          OutboxWriter outbox) {
        this.signatures = signatures;
        this.payments = payments;
        this.events = events;
        this.outbox = outbox;
    }

    @Transactional
    public ProcessingResult process(byte[] rawBody, String signature, String gatewayEventId) {
        if (!signatures.verifyWebhook(rawBody, signature)) {
            log.warn("Rejected a webhook call with an invalid signature");
            throw ApiException.badRequest("Invalid webhook signature");
        }
        String eventId = gatewayEventId == null || gatewayEventId.isBlank() ? "body-" + sha256(rawBody) : gatewayEventId.strip();
        if (events.existsByGatewayEventId(eventId)) {
            log.info("Webhook event {} already processed, ignoring", eventId);
            return ProcessingResult.IGNORED;
        }

        String json = new String(rawBody, StandardCharsets.UTF_8);
        Map<String, Object> body;
        try {
            body = MAPPER.readValue(rawBody, MAP);
        } catch (JacksonException e) {
            throw ApiException.badRequest("The webhook body is not valid JSON");
        }
        String type = text(body.get("event"));
        Map<String, Object> paymentEntity = entity(body, "payment");
        Map<String, Object> orderEntity = entity(body, "order");
        String gatewayOrderRef = firstNonNull(text(paymentEntity.get("order_id")), text(orderEntity.get("id")));
        String gatewayPaymentRef = text(paymentEntity.get("id"));

        Optional<Payment> found = gatewayOrderRef == null ? Optional.empty()
                : payments.findByGatewayNameAndGatewayOrderRef(Payment.GATEWAY, gatewayOrderRef);
        if (found.isEmpty()) {
            log.warn("Webhook {} ({}) is for an unknown gateway order {}", eventId, type, gatewayOrderRef);
            return record(null, eventId, type, json, ProcessingResult.UNMATCHED);
        }
        Payment payment = found.get();

        ProcessingResult result = switch (type == null ? "" : type) {
            case "payment.captured", "order.paid" -> success(payment, gatewayPaymentRef, paymentEntity);
            case "payment.failed" -> failure(payment, gatewayPaymentRef, paymentEntity);
            default -> ProcessingResult.IGNORED;
        };
        return record(payment.getId(), eventId, type, json, result);
    }

    private ProcessingResult success(Payment payment, String gatewayPaymentRef, Map<String, Object> paymentEntity) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return ProcessingResult.IGNORED;   // e.g. order.paid arriving after payment.captured
        }
        Long paidMinor = number(paymentEntity.get("amount"));
        if (paidMinor != null && paidMinor != toMinorUnits(payment.getAmount())) {
            log.error("AMOUNT MISMATCH for payment {}: gateway reports {} but we expected {}. Not marking it as paid.",
                    payment.getId(), paidMinor, toMinorUnits(payment.getAmount()));
            return ProcessingResult.AMOUNT_MISMATCH;
        }
        if (payments.existsByOrderIdAndStatus(payment.getOrderId(), PaymentStatus.SUCCESS)) {
            log.error("DUPLICATE PAYMENT: order {} is already paid by another payment; payment {} ({}) needs a manual refund.",
                    payment.getOrderId(), payment.getId(), gatewayPaymentRef);
            return ProcessingResult.DUPLICATE_SUCCESS;
        }
        if (payment.markSuccess(gatewayPaymentRef)) {
            payments.saveAndFlush(payment);
            outbox.paymentEvent(EventTypes.PAYMENT_SUCCEEDED, payment);
            return ProcessingResult.PROCESSED;
        }
        return ProcessingResult.IGNORED;
    }

    private ProcessingResult failure(Payment payment, String gatewayPaymentRef, Map<String, Object> paymentEntity) {
        String reason = firstNonNull(text(paymentEntity.get("error_description")), text(paymentEntity.get("error_reason")), "Payment failed");
        if (payment.markFailed(reason, gatewayPaymentRef)) {
            payments.saveAndFlush(payment);
            outbox.paymentEvent(EventTypes.PAYMENT_FAILED, payment);
            return ProcessingResult.PROCESSED;
        }
        return ProcessingResult.IGNORED;   // already SUCCESS or already FAILED
    }

    private ProcessingResult record(java.util.UUID paymentId, String eventId, String type, String json, ProcessingResult result) {
        events.saveAndFlush(PaymentEvent.of(paymentId, eventId, type == null ? "unknown" : type, json, result));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entity(Map<String, Object> body, String name) {
        if (body.get("payload") instanceof Map<?, ?> payload && payload.get(name) instanceof Map<?, ?> wrapper
                && wrapper.get("entity") instanceof Map<?, ?> entity) {
            return (Map<String, Object>) entity;
        }
        return Map.of();
    }

    private static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private static Long number(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
