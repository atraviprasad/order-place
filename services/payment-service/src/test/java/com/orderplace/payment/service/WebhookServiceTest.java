package com.orderplace.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    private static final String SECRET = "webhook-secret";

    @Mock PaymentRepository payments;
    @Mock PaymentEventRepository events;
    @Mock OutboxWriter outbox;

    private WebhookService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new WebhookService(new SignatureVerifier("key-secret", SECRET), payments, events, outbox);
        payment = Payment.initiate(UUID.randomUUID(), "ORD-1", UUID.randomUUID(), UUID.randomUUID(), "a@b.com",
                new BigDecimal("1200.50"), "INR", "order_G1", "k1");
    }

    private static byte[] body(String event, long amountPaise) {
        return ("{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_G1\","
                + "\"amount\":" + amountPaise + ",\"error_description\":\"Card declined\"}}}}").getBytes(StandardCharsets.UTF_8);
    }

    private static String sig(byte[] b) {
        return SignatureVerifierTestSupport.hmac(SECRET, b);
    }

    private void paymentFound() {
        when(payments.findByGatewayNameAndGatewayOrderRef(Payment.GATEWAY, "order_G1")).thenReturn(Optional.of(payment));
    }

    private ProcessingResult recorded() {
        ArgumentCaptor<PaymentEvent> c = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).saveAndFlush(c.capture());
        return c.getValue().getProcessingResult();
    }

    @Test
    void invalidSignatureIsRejectedAndNothingStored() {
        byte[] b = body("payment.captured", 120050);
        assertThatThrownBy(() -> service.process(b, "bad", "evt_1")).isInstanceOf(ApiException.class);
        verify(events, never()).saveAndFlush(any());
        verify(outbox, never()).paymentEvent(any(), any());
    }

    @Test
    void capturedMarksPaymentSuccessAndWritesOutboxEvent() {
        byte[] b = body("payment.captured", 120050);
        when(events.existsByGatewayEventId("evt_1")).thenReturn(false);
        paymentFound();
        when(payments.existsByOrderIdAndStatus(payment.getOrderId(), PaymentStatus.SUCCESS)).thenReturn(false);

        ProcessingResult r = service.process(b, sig(b), "evt_1");

        assertThat(r).isEqualTo(ProcessingResult.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(outbox).paymentEvent(EventTypes.PAYMENT_SUCCEEDED, payment);
        assertThat(recorded()).isEqualTo(ProcessingResult.PROCESSED);
    }

    @Test
    void duplicateEventIdIsIgnored() {
        byte[] b = body("payment.captured", 120050);
        when(events.existsByGatewayEventId("evt_1")).thenReturn(true);

        assertThat(service.process(b, sig(b), "evt_1")).isEqualTo(ProcessingResult.IGNORED);
        verify(payments, never()).saveAndFlush(any());
        verify(outbox, never()).paymentEvent(any(), any());
    }

    @Test
    void missingEventIdFallsBackToBodyHash() {
        byte[] b = body("payment.captured", 120050);
        when(events.existsByGatewayEventId(org.mockito.ArgumentMatchers.startsWith("body-"))).thenReturn(true);

        assertThat(service.process(b, sig(b), null)).isEqualTo(ProcessingResult.IGNORED);
    }

    @Test
    void orderPaidAfterCapturedIsIgnored() {
        byte[] b = body("order.paid", 120050);
        payment.markSuccess("pay_1");
        when(events.existsByGatewayEventId("evt_2")).thenReturn(false);
        paymentFound();

        assertThat(service.process(b, sig(b), "evt_2")).isEqualTo(ProcessingResult.IGNORED);
        verify(outbox, never()).paymentEvent(any(), any());
    }

    @Test
    void amountMismatchIsRecordedButNotApplied() {
        byte[] b = body("payment.captured", 100);
        when(events.existsByGatewayEventId("evt_3")).thenReturn(false);
        paymentFound();

        assertThat(service.process(b, sig(b), "evt_3")).isEqualTo(ProcessingResult.AMOUNT_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(outbox, never()).paymentEvent(any(), any());
    }

    @Test
    void secondSuccessForSameOrderIsFlaggedAsDuplicate() {
        byte[] b = body("payment.captured", 120050);
        when(events.existsByGatewayEventId("evt_4")).thenReturn(false);
        paymentFound();
        when(payments.existsByOrderIdAndStatus(payment.getOrderId(), PaymentStatus.SUCCESS)).thenReturn(true);

        assertThat(service.process(b, sig(b), "evt_4")).isEqualTo(ProcessingResult.DUPLICATE_SUCCESS);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(outbox, never()).paymentEvent(any(), any());
    }

    @Test
    void failedMarksPaymentFailedWithReasonAndWritesOutboxEvent() {
        byte[] b = body("payment.failed", 120050);
        when(events.existsByGatewayEventId("evt_5")).thenReturn(false);
        paymentFound();

        assertThat(service.process(b, sig(b), "evt_5")).isEqualTo(ProcessingResult.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Card declined");
        verify(outbox).paymentEvent(eq(EventTypes.PAYMENT_FAILED), eq(payment));
    }

    @Test
    void failureNeverUndoesSuccess() {
        byte[] b = body("payment.failed", 120050);
        payment.markSuccess("pay_1");
        when(events.existsByGatewayEventId("evt_6")).thenReturn(false);
        paymentFound();

        assertThat(service.process(b, sig(b), "evt_6")).isEqualTo(ProcessingResult.IGNORED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void successAfterEarlierFailureWins() {
        byte[] b = body("payment.captured", 120050);
        payment.markFailed("Card declined", "pay_0");
        when(events.existsByGatewayEventId("evt_7")).thenReturn(false);
        paymentFound();
        when(payments.existsByOrderIdAndStatus(payment.getOrderId(), PaymentStatus.SUCCESS)).thenReturn(false);

        assertThat(service.process(b, sig(b), "evt_7")).isEqualTo(ProcessingResult.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void unknownGatewayOrderIsAcknowledgedAndRecordedAsUnmatched() {
        byte[] b = body("payment.captured", 120050);
        when(events.existsByGatewayEventId("evt_8")).thenReturn(false);
        when(payments.findByGatewayNameAndGatewayOrderRef(Payment.GATEWAY, "order_G1")).thenReturn(Optional.empty());

        assertThat(service.process(b, sig(b), "evt_8")).isEqualTo(ProcessingResult.UNMATCHED);
        assertThat(recorded()).isEqualTo(ProcessingResult.UNMATCHED);
    }

    @Test
    void unknownEventTypeIsIgnored() {
        byte[] b = body("refund.created", 120050);
        when(events.existsByGatewayEventId("evt_9")).thenReturn(false);
        paymentFound();

        assertThat(service.process(b, sig(b), "evt_9")).isEqualTo(ProcessingResult.IGNORED);
    }

    @Test
    void malformedJsonWithValidSignatureIsBadRequest() {
        byte[] b = "not json".getBytes(StandardCharsets.UTF_8);
        when(events.existsByGatewayEventId(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.process(b, sig(b), "evt_10")).isInstanceOf(ApiException.class);
    }
}
