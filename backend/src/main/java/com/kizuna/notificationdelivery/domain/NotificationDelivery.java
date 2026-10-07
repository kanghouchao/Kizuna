package com.kizuna.notificationdelivery.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_notification_deliveries")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends StoreScopedEntity {
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private DeliveryContent.SourceType sourceType;

  @Column(updatable = false, length = 32)
  private String orderId;

  @Column(updatable = false, length = 32)
  private String applicationId;

  @Column(nullable = false, updatable = false, length = 20)
  private String channel;

  @Column(nullable = false, updatable = false, length = 20)
  private String purpose;

  @Column(nullable = false, updatable = false, length = 200)
  private String subject;

  @Column(nullable = false, updatable = false, length = 10000)
  private String body;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime scheduledAt;

  @Column(nullable = false, updatable = false, length = 120)
  private String dedupeKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DeliveryStatus status;

  @Column(nullable = false)
  private int attemptCount;

  @Column(length = 500)
  private String reason;

  public static NotificationDelivery draft(DeliveryContent input) {
    var row = new NotificationDelivery();
    row.sourceType = input.sourceType();
    if (input.sourceType() == DeliveryContent.SourceType.ORDER) row.orderId = input.sourceId();
    else row.applicationId = input.sourceId();
    row.channel = input.channel();
    row.purpose = input.purpose();
    row.subject = input.subject();
    row.body = input.body();
    row.scheduledAt = input.scheduledAt();
    row.dedupeKey = input.dedupeKey();
    row.status = DeliveryStatus.DRAFT;
    return row;
  }

  public String sourceId() {
    return sourceType == DeliveryContent.SourceType.ORDER ? orderId : applicationId;
  }

  public DeliveryContent content() {
    return new DeliveryContent(
        sourceType, sourceId(), channel, purpose, subject, body, scheduledAt, dedupeKey);
  }

  public void requireSame(DeliveryContent input) {
    if (!content().equals(input)) throw new ConflictException("同じ要求キーに異なる通知が指定されています");
  }

  public void queue(Long version, String reason, boolean retry) {
    if (!Objects.equals(getVersion(), version)
        || (retry
            ? status != DeliveryStatus.FAILED && status != DeliveryStatus.BLOCKED
            : status != DeliveryStatus.DRAFT))
      throw new ConflictException("通知の状態が変わりました。再取得して確認してください");
    this.reason = DeliveryContent.clean(reason, 500);
    status = DeliveryStatus.QUEUED;
  }

  public void dispatch() {
    require(DeliveryStatus.QUEUED);
    status = DeliveryStatus.DISPATCHED;
    attemptCount++;
  }

  public void sending() {
    require(DeliveryStatus.DISPATCHED);
    status = DeliveryStatus.SENDING;
  }

  public void finish(DeliveryStatus result) {
    if (status != DeliveryStatus.DISPATCHED && status != DeliveryStatus.SENDING)
      throw new ConflictException("この通知の結果は変更できません");
    if (result != DeliveryStatus.SENT
        && result != DeliveryStatus.FAILED
        && result != DeliveryStatus.BLOCKED
        && result != DeliveryStatus.UNKNOWN) throw new IllegalArgumentException("未定義の送信結果です");
    status = result;
  }

  private void require(DeliveryStatus expected) {
    if (status != expected) throw new ConflictException("通知の状態が変わりました");
  }
}
