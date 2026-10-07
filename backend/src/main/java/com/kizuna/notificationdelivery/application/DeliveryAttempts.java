package com.kizuna.notificationdelivery.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.notification.transport.EmailTransport;
import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryAttemptRepository;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.order.contact.BusinessContactDecision;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryAttempts {
  private final NotificationDeliveryRepository deliveries;
  private final DeliveryAttemptRepository attempts;
  private final DeliveryAuthorization authorization;
  private final DeliveryContact contacts;
  private final DeliveryAudit audit;
  private final StoreContext store;
  private final Clock clock;

  public record Envelope(String recipient, String subject, String body) {}

  private record Locked(NotificationDelivery delivery, DeliveryAttempt attempt) {}

  @StoreScoped
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean claim(DeliveryDispatch event) {
    var locked = lock(event);
    if (locked.attempt().getStatus() == DeliveryStatus.SENDING) {
      finish(locked, event, DeliveryStatus.UNKNOWN, "INTERRUPTED");
      return false;
    }
    if (locked.attempt().getStatus() != DeliveryStatus.DISPATCHED) return false;
    if (!allowed(locked, event)) return false;
    locked.attempt().sending();
    locked.delivery().sending();
    audit.append(locked.delivery(), actor(locked), "NOTIFICATION_SENDING");
    return true;
  }

  @StoreScoped
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Envelope prepare(DeliveryDispatch event) {
    var locked = lock(event);
    if (locked.attempt().getStatus() != DeliveryStatus.SENDING || !allowed(locked, event))
      return null;
    var decision = contacts.decide(locked.delivery().content());
    if (decision.decision() != BusinessContactDecision.ALLOWED) {
      finish(locked, event, DeliveryStatus.BLOCKED, "CONTACT_NOT_ALLOWED");
      return null;
    }
    return new Envelope(
        decision.value(), locked.delivery().getSubject(), locked.delivery().getBody());
  }

  @StoreScoped
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void complete(DeliveryDispatch event, EmailTransport.Result result) {
    var locked = lock(event);
    if (locked.attempt().getStatus() != DeliveryStatus.SENDING) return;
    var status =
        switch (result) {
          case SENT -> DeliveryStatus.SENT;
          case UNKNOWN -> DeliveryStatus.UNKNOWN;
          case FAILED, UNAVAILABLE -> DeliveryStatus.FAILED;
        };
    finish(locked, event, status, result == EmailTransport.Result.SENT ? null : result.name());
  }

  private boolean allowed(Locked locked, DeliveryDispatch event) {
    try {
      authorization.require(event.serviceUserId(), event.storeId());
      if (contacts.decide(locked.delivery().content()).decision()
          != BusinessContactDecision.ALLOWED) {
        finish(locked, event, DeliveryStatus.BLOCKED, "CONTACT_NOT_ALLOWED");
        return false;
      }
      return true;
    } catch (AccessDeniedException denied) {
      finish(locked, event, DeliveryStatus.BLOCKED, "AUTHORIZATION_DENIED");
      return false;
    } catch (NotFoundException missing) {
      finish(locked, event, DeliveryStatus.BLOCKED, "SOURCE_UNAVAILABLE");
      return false;
    }
  }

  private Locked lock(DeliveryDispatch event) {
    if (!store.hasStoreId() || !store.getStoreId().equals(event.storeId()))
      throw new AccessDeniedException("店舗文脈が不正です");
    var row =
        deliveries
            .lockById(event.deliveryId())
            .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
    var attempt =
        attempts
            .findById(event.attemptId())
            .orElseThrow(() -> new NotFoundException("送信試行が見つかりません"));
    if (!attempt.getDeliveryId().equals(row.getId())
        || !attempt.getServiceUserId().equals(event.serviceUserId())
        || !attempt.getTaskExecutionId().equals(event.executionId()))
      throw new AccessDeniedException("送信試行の指定が不正です");
    return new Locked(row, attempt);
  }

  private void finish(Locked locked, DeliveryDispatch event, DeliveryStatus status, String code) {
    locked.attempt().finish(status, code, OffsetDateTime.now(clock));
    locked.delivery().finish(status);
    audit.append(locked.delivery(), actor(locked), "NOTIFICATION_RESULT");
  }

  private static AuditActor actor(Locked locked) {
    return new AuditActor(
        locked.attempt().getServiceUserId(), "SERVICE", locked.attempt().getServiceName());
  }
}
