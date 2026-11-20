package com.orderplace.notification.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orderplace.common.web.ApiException;
import com.orderplace.notification.mail.EmailSender;
import com.orderplace.notification.model.Notification;
import com.orderplace.notification.model.NotificationStatus;
import com.orderplace.notification.model.RecipientRole;
import com.orderplace.notification.repository.NotificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock MongoTemplate mongo;
    @Mock NotificationRepository repository;
    @Mock EmailSender sender;

    private AdminNotificationService service() {
        return new AdminNotificationService(mongo, repository, sender);
    }

    private static Notification failed(String recipient, String body) {
        Notification n = Notification.create("e1", "OrderSubmitted", "o1", "OP-1", RecipientRole.DEALER, recipient, null);
        n.prepare("Subject", body);
        n.recordFailure("smtp down");
        n.markFailed("smtp down");
        return n;
    }

    private static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void retry_resendsAFailedNotification() {
        Notification n = failed("d@x.com", "<html/>");
        when(repository.findById("n1")).thenReturn(Optional.of(n));
        when(repository.save(n)).thenReturn(n);

        NotificationResponse response = service().retry("n1");

        verify(sender).send("d@x.com", "Subject", "<html/>");
        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.attempts()).isEqualTo(2);
    }

    @Test
    void retry_failingAgain_staysFailedWithTheNewError() {
        Notification n = failed("d@x.com", "<html/>");
        when(repository.findById("n1")).thenReturn(Optional.of(n));
        when(repository.save(n)).thenReturn(n);
        doThrow(new MailSendException("still down")).when(sender).send(anyString(), anyString(), anyString());

        NotificationResponse response = service().retry("n1");

        assertThat(response.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(response.lastError()).contains("still down");
    }

    @Test
    void retry_onlyForFailedNotifications() {
        Notification sent = failed("d@x.com", "<html/>");
        sent.markSent();
        when(repository.findById("n1")).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> service().retry("n1")).satisfies(t -> assertCode(t, "NOT_RETRYABLE"));
        verifyNoInteractions(sender);
    }

    @Test
    void retry_withoutRecipient_isRejected() {
        when(repository.findById("n1")).thenReturn(Optional.of(failed(null, null)));

        assertThatThrownBy(() -> service().retry("n1")).satisfies(t -> assertCode(t, "NOT_RETRYABLE"));
        verifyNoInteractions(sender);
    }

    @Test
    void retry_unknownId_isNotFound() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().retry("nope")).satisfies(t -> assertCode(t, "NOT_FOUND"));
    }
}
