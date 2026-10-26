package com.kizuna.notificationdelivery.application;

import com.kizuna.notification.transport.EmailTransport;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

@Component
@RequiredArgsConstructor
public class DeliveryListener {
  private final DeliveryAttempts attempts;
  private final EmailTransport transport;

  @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
  public void deliver(DeliveryDispatch event) {
    if (!attempts.claim(event)) return;
    var envelope = attempts.prepare(event);
    if (envelope == null) return;
    EmailTransport.Result result;
    try {
      result = transport.deliver(envelope.recipient(), envelope.subject(), envelope.body());
    } catch (RuntimeException uncertain) {
      result = EmailTransport.Result.UNKNOWN;
    }
    attempts.complete(event, result);
  }
}
