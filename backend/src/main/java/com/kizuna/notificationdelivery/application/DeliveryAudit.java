package com.kizuna.notificationdelivery.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryAudit {
  private final AuditWriter writer;

  public void append(NotificationDelivery d, AuditActor actor, String action) {
    writer.append(
        new AuditChange(
            actor,
            d.getStoreId(),
            action,
            "NOTIFICATION_DELIVERY",
            d.getId(),
            d.getSourceType().name(),
            d.sourceId(),
            Map.of(),
            Map.of(
                "status",
                d.getStatus().name(),
                "attempt_count",
                Integer.toString(d.getAttemptCount()))));
  }
}
