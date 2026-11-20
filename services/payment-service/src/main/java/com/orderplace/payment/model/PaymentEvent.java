package com.orderplace.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

/** A webhook call with a valid signature. The unique gateway_event_id makes processing idempotent. */
@Entity
@Table(name = "payment_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "gateway_event_id", nullable = false, updatable = false, length = 100)
    private String gatewayEventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 60)
    private String eventType;

    @Column(name = "raw_payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_result", nullable = false, updatable = false, length = 30)
    private ProcessingResult processingResult;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public static PaymentEvent of(UUID paymentId, String gatewayEventId, String eventType, String rawPayload,
                                  ProcessingResult result) {
        PaymentEvent e = new PaymentEvent();
        e.paymentId = paymentId;
        e.gatewayEventId = gatewayEventId;
        e.eventType = eventType;
        e.rawPayload = rawPayload;
        e.processingResult = result;
        e.receivedAt = Instant.now();
        return e;
    }
}
