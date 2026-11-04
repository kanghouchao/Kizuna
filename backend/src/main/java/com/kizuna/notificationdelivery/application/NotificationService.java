package com.kizuna.notificationdelivery.application;

import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryAttemptRepository;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
  private final NotificationDeliveryRepository deliveries;
  private final DeliveryAttemptRepository attempts;
  private final NotificationActors actors;
  private final DeliveryContact contacts;
  private final DeliveryAudit audit;
  private final DeliveryTransports transports;
  private final Clock clock;

  public record Created(DeliveryView delivery, boolean created) {}

  @Transactional
  public Created create(Long storeId, String email, DeliveryContent content) {
    var actor = actors.require(storeId, email, PermissionCode.NOTIFICATION_MANAGE);
    var existing = deliveries.findByAttributedStoreIdAndDedupeKey(storeId, content.dedupeKey());
    if (existing.isPresent()) {
      existing.get().requireSame(content);
      return new Created(response(storeId, existing.get()), false);
    }
    content.validateNewSchedule(OffsetDateTime.now(clock));
    contacts.resolve(storeId, content);
    var row = deliveries.saveAndFlush(NotificationDelivery.draft(storeId, content));
    audit.append(row, actor, "NOTIFICATION_CREATED");
    return new Created(response(storeId, row), true);
  }

  @Transactional(readOnly = true)
  public Created replay(Long storeId, String email, DeliveryContent content) {
    actors.require(storeId, email, PermissionCode.NOTIFICATION_MANAGE);
    var row =
        deliveries
            .findByAttributedStoreIdAndDedupeKey(storeId, content.dedupeKey())
            .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
    row.requireSame(content);
    return new Created(response(storeId, row), false);
  }

  @Transactional(readOnly = true)
  public Page<NotificationDelivery> list(Long storeId, String email, Pageable pageable) {
    actors.require(storeId, email, PermissionCode.NOTIFICATION_VIEW);
    return deliveries.findByAttributedStoreId(storeId, pageable);
  }

  @Transactional(readOnly = true)
  public DeliveryView get(Long storeId, String email, String id) {
    actors.require(storeId, email, PermissionCode.NOTIFICATION_VIEW);
    return response(storeId, require(storeId, id));
  }

  @Transactional
  public DeliveryView queue(
      Long storeId, String email, String id, Long version, String reason, boolean retry) {
    var actor = actors.require(storeId, email, PermissionCode.NOTIFICATION_SEND);
    var row =
        deliveries
            .findByIdAndAttributedStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
    contacts.resolve(storeId, row.content());
    row.queue(version, reason, retry);
    deliveries.flush();
    audit.append(row, actor, retry ? "NOTIFICATION_RETRIED" : "NOTIFICATION_QUEUED");
    return response(storeId, row);
  }

  @Transactional(readOnly = true)
  public Page<DeliveryAttempt> history(Long storeId, String email, String id, Pageable pageable) {
    actors.require(storeId, email, PermissionCode.NOTIFICATION_VIEW);
    require(storeId, id);
    return attempts.findByDeliveryId(id, pageable);
  }

  private NotificationDelivery require(Long storeId, String id) {
    return deliveries
        .findByIdAndAttributedStoreId(id, storeId)
        .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
  }

  private DeliveryView response(Long storeId, NotificationDelivery row) {
    return new DeliveryView(
        row,
        contacts.resolve(storeId, row.content()).decision(),
        transports.of(row.getChannel()).available());
  }
}
