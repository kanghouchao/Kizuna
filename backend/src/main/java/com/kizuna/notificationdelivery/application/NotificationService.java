package com.kizuna.notificationdelivery.application;

import com.kizuna.notification.transport.EmailTransport;
import com.kizuna.notificationdelivery.api.dto.DeliveryAttemptResponse;
import com.kizuna.notificationdelivery.api.dto.DeliveryResponse;
import com.kizuna.notificationdelivery.api.dto.DeliverySummary;
import com.kizuna.notificationdelivery.domain.DeliveryAttemptRepository;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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
  private final EmailTransport transport;
  private final Clock clock;

  public record Created(DeliveryResponse delivery, boolean created) {}

  @StoreScoped
  @Transactional
  public Created create(Long storeId, String email, DeliveryContent content) {
    var actor = actors.require(storeId, email, PermissionCode.NOTIFICATION_MANAGE);
    var existing = deliveries.findByStoreIdAndDedupeKey(storeId, content.dedupeKey());
    if (existing.isPresent()) {
      existing.get().requireSame(content);
      return new Created(response(storeId, existing.get()), false);
    }
    content.validateNewSchedule(OffsetDateTime.now(clock));
    contacts.decide(storeId, content);
    var row = deliveries.saveAndFlush(NotificationDelivery.draft(storeId, content));
    audit.append(row, actor, "NOTIFICATION_CREATED");
    return new Created(response(storeId, row), true);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Created replay(Long storeId, String email, DeliveryContent content) {
    actors.require(storeId, email, PermissionCode.NOTIFICATION_MANAGE);
    var row =
        deliveries
            .findByStoreIdAndDedupeKey(storeId, content.dedupeKey())
            .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
    row.requireSame(content);
    return new Created(response(storeId, row), false);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<DeliverySummary> list(Long storeId, String email, String cursor, int size) {
    actors.require(storeId, email, PermissionCode.NOTIFICATION_VIEW);
    checkSize(size);
    var rows =
        deliveries.findByStoreIdAndIdLessThanOrderByIdDesc(
            storeId, before(cursor), Limit.of(size + 1));
    return CursorPage.of(rows, size, d -> PageCursor.encodeKey(d.getId())).map(DeliverySummary::of);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public DeliveryResponse get(Long storeId, String email, String id) {
    actors.require(storeId, email, PermissionCode.NOTIFICATION_VIEW);
    return response(storeId, require(storeId, id));
  }

  @StoreScoped
  @Transactional
  public DeliveryResponse queue(
      Long storeId, String email, String id, Long version, String reason, boolean retry) {
    var actor = actors.require(storeId, email, PermissionCode.NOTIFICATION_SEND);
    var row =
        deliveries
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
    contacts.decide(storeId, row.content());
    row.queue(version, reason, retry);
    deliveries.flush();
    audit.append(row, actor, retry ? "NOTIFICATION_RETRIED" : "NOTIFICATION_QUEUED");
    return response(storeId, row);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<DeliveryAttemptResponse> history(
      Long storeId, String email, String id, String cursor, int size) {
    actors.require(storeId, email, PermissionCode.NOTIFICATION_VIEW);
    require(storeId, id);
    checkSize(size);
    return CursorPage.of(
            attempts.findByStoreIdAndDeliveryIdAndIdLessThanOrderByIdDesc(
                storeId, id, before(cursor), Limit.of(size + 1)),
            size,
            a -> PageCursor.encodeKey(a.getId()))
        .map(DeliveryAttemptResponse::of);
  }

  private NotificationDelivery require(Long storeId, String id) {
    return deliveries
        .findByIdAndStoreId(id, storeId)
        .orElseThrow(() -> new NotFoundException("通知が見つかりません"));
  }

  private DeliveryResponse response(Long storeId, NotificationDelivery row) {
    return DeliveryResponse.of(
        row, contacts.decide(storeId, row.content()).decision().name(), transport.available());
  }

  private static String before(String cursor) {
    if (cursor == null) return "~";
    var id = PageCursor.decodeKey(cursor);
    if (!id.matches("[0-9]{1,32}")) throw new ServiceException("続きの位置が不正です");
    return id;
  }

  private static void checkSize(int size) {
    if (size < 1 || size > 100) throw new ServiceException("取得件数は1〜100件で指定してください");
  }
}
