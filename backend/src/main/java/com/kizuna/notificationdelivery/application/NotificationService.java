package com.kizuna.notificationdelivery.application;

import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryAttemptRepository;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryResolution;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.Timestamps;
import com.kizuna.user.application.ActorIdentityService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
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
  private final ActorIdentityService identities;
  private final DeliveryContact contacts;
  private final DeliveryAudit audit;
  private final DeliveryTransports transports;
  private final Clock clock;

  public record Created(DeliveryView delivery, boolean created) {}

  @Transactional
  public Created create(Long storeId, String email, DeliveryContent content) {
    var actor = identities.requireActor(email);
    var existing = byKey(storeId, content.dedupeKey());
    if (existing.isPresent()) {
      existing.get().requireSame(content);
      return new Created(response(storeId, existing.get()), false);
    }
    content.validateNewSchedule(OffsetDateTime.now(clock));
    var draft = NotificationDelivery.draft(storeId, content);
    contacts.resolve(storeId, content);
    var row = deliveries.saveAndFlush(draft);
    audit.append(row, actor, "NOTIFICATION_CREATED");
    return new Created(response(storeId, row), true);
  }

  @Transactional(readOnly = true)
  public Created replay(Long storeId, DeliveryContent content) {
    var row =
        byKey(storeId, content.dedupeKey()).orElseThrow(() -> new NotFoundException("通知が見つかりません"));
    row.requireSame(content);
    return new Created(response(storeId, row), false);
  }

  @Transactional(readOnly = true)
  public Page<NotificationDelivery> list(Long storeId, Pageable pageable) {
    return storeId == null
        ? deliveries.findByAttributedStoreIdIsNull(pageable)
        : deliveries.findByAttributedStoreId(storeId, pageable);
  }

  @Transactional(readOnly = true)
  public DeliveryView get(Long storeId, String id) {
    return response(storeId, require(storeId, id));
  }

  @Transactional
  public DeliveryView queue(
      Long storeId, String email, String id, Long version, String reason, boolean retry) {
    var actor = identities.requireActor(email);
    var row = lock(storeId, id);
    contacts.resolve(storeId, row.content());
    row.queue(version, reason, retry);
    deliveries.flush();
    audit.append(row, actor, retry ? "NOTIFICATION_RETRIED" : "NOTIFICATION_QUEUED");
    return response(storeId, row);
  }

  @Transactional
  public DeliveryView resolve(
      Long storeId,
      String email,
      String id,
      Long version,
      DeliveryResolution outcome,
      String reason) {
    var actor = identities.requireActor(email);
    var row = lock(storeId, id);
    row.resolve(version, outcome, reason);
    deliveries.flush();
    attempts.saveAndFlush(
        DeliveryAttempt.resolution(row, actor.id(), actor.name(), Timestamps.now()));
    audit.append(row, actor, "NOTIFICATION_RESOLVED");
    return response(storeId, row);
  }

  @Transactional(readOnly = true)
  public Page<DeliveryAttempt> history(Long storeId, String id, Pageable pageable) {
    require(storeId, id);
    return attempts.findByDeliveryId(id, pageable);
  }

  // storeId が null なら会員宛（店舗を持たない）の行。店舗の行とは別の照会で引く。
  private Optional<NotificationDelivery> byKey(Long storeId, String key) {
    return storeId == null
        ? deliveries.findByAttributedStoreIdIsNullAndDedupeKey(key)
        : deliveries.findByAttributedStoreIdAndDedupeKey(storeId, key);
  }

  private NotificationDelivery require(Long storeId, String id) {
    return (storeId == null
            ? deliveries.findByIdAndAttributedStoreIdIsNull(id)
            : deliveries.findByIdAndAttributedStoreId(id, storeId))
        .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
  }

  private NotificationDelivery lock(Long storeId, String id) {
    return (storeId == null
            ? deliveries.findByIdAndAttributedStoreIdIsNullForUpdate(id)
            : deliveries.findByIdAndAttributedStoreIdForUpdate(id, storeId))
        .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
  }

  private DeliveryView response(Long storeId, NotificationDelivery row) {
    return new DeliveryView(
        row,
        contacts.resolve(storeId, row.content()).decision(),
        transports.of(row.getChannel()).available());
  }
}
