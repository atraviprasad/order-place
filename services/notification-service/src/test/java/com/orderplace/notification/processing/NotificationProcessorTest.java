package com.orderplace.notification.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orderplace.common.events.EventEnvelope;
import com.orderplace.common.events.EventTypes;
import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.notification.mail.EmailRenderer;
import com.orderplace.notification.mail.EmailSender;
import com.orderplace.notification.model.Notification;
import com.orderplace.notification.model.NotificationStatus;
import com.orderplace.notification.model.RecipientRole;
import com.orderplace.notification.repository.NotificationRepository;
import com.orderplace.notification.rules.NotificationRules;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationProcessorTest {

    @Mock NotificationRepository repository;
    @Mock EmailRenderer renderer;
    @Mock EmailSender sender;

    private final NotificationRules rules = new NotificationRules();
    private final UUID eventId = UUID.randomUUID();

    private NotificationProcessor processor() {
        NotificationProcessor p = new NotificationProcessor(rules, repository, renderer, sender);
        ReflectionTestUtils.setField(p, "maxAttempts", 3);
        return p;
    }

    private EventEnvelope<OrderEventPayload> event(String type, String customerEmail, String dealerEmail) {
        OrderEventPayload payload = new OrderEventPayload(UUID.randomUUID(), "OP-2026-000007", UUID.randomUUID(),
                "Demo Customer", UUID.randomUUID(), customerEmail, dealerEmail, new BigDecimal("1000.00"), "INR", null);
        return new EventEnvelope<>(eventId, type, 1, Instant.now(), "corr-1", payload.orderId(), payload);
    }

    private void noExistingRow(RecipientRole role) {
        when(repository.findByEventIdAndRecipientRole(eventId.toString(), role)).thenReturn(Optional.empty());
        when(repository.insert(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submittedOrder_isSentToTheDealerAndLogged() {
        noExistingRow(RecipientRole.DEALER);
        EventEnvelope<OrderEventPayload> submitted = event(EventTypes.ORDER_SUBMITTED, "c@x.com", "d@x.com");
        when(renderer.render("OrderSubmitted", RecipientRole.DEALER, submitted.payload())).thenReturn("<html>body</html>");

        processor().handle(submitted);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getRecipient()).isEqualTo("d@x.com");
        assertThat(n.getRecipientRole()).isEqualTo(RecipientRole.DEALER);
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(n.getSentAt()).isNotNull();
        assertThat(n.getSubject()).contains("OP-2026-000007");
        assertThat(n.getBody()).isEqualTo("<html>body</html>");
        verify(sender).send("d@x.com", n.getSubject(), "<html>body</html>");
    }

    @Test
    void approvedOrder_isSentToTheCustomer() {
        noExistingRow(RecipientRole.CUSTOMER);
        when(renderer.render(anyString(), any(), any())).thenReturn("html");

        processor().handle(event(EventTypes.ORDER_APPROVED, "c@x.com", "d@x.com"));

        verify(sender).send(anyString(), anyString(), anyString());
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRecipient()).isEqualTo("c@x.com");
    }

    @Test
    void unknownEventType_isIgnored() {
        processor().handle(event("SomethingElse", "c@x.com", "d@x.com"));

        verifyNoInteractions(repository, sender, renderer);
    }

    @Test
    void sameEventTwice_isSentOnlyOnce() {
        Notification sent = Notification.create(eventId.toString(), EventTypes.ORDER_SUBMITTED, "o", "n",
                RecipientRole.DEALER, "d@x.com", null);
        sent.markSent();
        when(repository.findByEventIdAndRecipientRole(eventId.toString(), RecipientRole.DEALER)).thenReturn(Optional.of(sent));

        processor().handle(event(EventTypes.ORDER_SUBMITTED, "c@x.com", "d@x.com"));

        verifyNoInteractions(sender, renderer);
        verify(repository, never()).save(any());
    }

    @Test
    void raceOnInsert_usesTheRowTheOtherConsumerCreated() {
        Notification other = Notification.create(eventId.toString(), EventTypes.ORDER_SUBMITTED, "o", "n",
                RecipientRole.DEALER, "d@x.com", null);
        other.markSent();
        when(repository.findByEventIdAndRecipientRole(eventId.toString(), RecipientRole.DEALER))
                .thenReturn(Optional.empty(), Optional.of(other));
        when(repository.insert(any(Notification.class))).thenThrow(new DuplicateKeyException("dup"));

        processor().handle(event(EventTypes.ORDER_SUBMITTED, "c@x.com", "d@x.com"));

        verifyNoInteractions(sender);
    }

    @Test
    void earlierUnfinishedAttempt_isResumed() {
        Notification pending = Notification.create(eventId.toString(), EventTypes.ORDER_SUBMITTED, "o", "n",
                RecipientRole.DEALER, "d@x.com", null);
        when(repository.findByEventIdAndRecipientRole(eventId.toString(), RecipientRole.DEALER)).thenReturn(Optional.of(pending));
        when(renderer.render(anyString(), any(), any())).thenReturn("html");

        processor().handle(event(EventTypes.ORDER_SUBMITTED, "c@x.com", "d@x.com"));

        verify(sender).send(anyString(), anyString(), anyString());
        assertThat(pending.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void missingRecipientEmail_isLoggedAsFailedWithoutSending() {
        noExistingRow(RecipientRole.DEALER);

        processor().handle(event(EventTypes.ORDER_SUBMITTED, "c@x.com", null));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getValue().getLastError()).contains("No recipient");
        verifyNoInteractions(sender, renderer);
    }

    @Test
    void mailFailure_isRecordedAndRethrownSoKafkaRetries() {
        noExistingRow(RecipientRole.DEALER);
        when(renderer.render(anyString(), any(), any())).thenReturn("html");
        doThrow(new MailSendException("smtp down")).when(sender).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> processor().handle(event(EventTypes.ORDER_SUBMITTED, "c@x.com", "d@x.com")))
                .isInstanceOf(MailSendException.class);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getValue().getAttempts()).isEqualTo(1);
        assertThat(saved.getValue().getLastError()).contains("smtp down");
    }

    @Test
    void lastFailedAttempt_marksFailedAndStopsRetrying() {
        Notification retrying = Notification.create(eventId.toString(), EventTypes.ORDER_SUBMITTED, "o", "n",
                RecipientRole.DEALER, "d@x.com", null);
        retrying.prepare("subject", "html");
        retrying.recordFailure("first");
        retrying.recordFailure("second");           // two attempts used, maxAttempts is 3
        when(repository.findByEventIdAndRecipientRole(eventId.toString(), RecipientRole.DEALER)).thenReturn(Optional.of(retrying));
        doThrow(new MailSendException("still down")).when(sender).send(anyString(), anyString(), anyString());

        processor().handle(event(EventTypes.ORDER_SUBMITTED, "c@x.com", "d@x.com"));   // must not throw

        assertThat(retrying.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(retrying.getAttempts()).isEqualTo(3);
        assertThat(retrying.getLastError()).contains("still down");
        verify(repository).save(retrying);
    }
}
