package com.kizuna.notificationdelivery.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
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

@Entity
@Table(name = "t_notification_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends DeliveryRecord {
  @Column(updatable = false)
  private Long attributedStoreId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private DeliverySourceType sourceType;

  @Column(nullable = false, updatable = false, length = 32)
  private String sourceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private DeliveryChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private DeliveryPurpose purpose;

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

  /** 会員宛はグループ級（帰属店舗 null）、受注・ゲスト申請は店舗の行。店舗の職員が会員へ送る経路を作らない。 */
  public static NotificationDelivery draft(Long attributedStoreId, DeliveryContent input) {
    if ((input.sourceType() == DeliverySourceType.MEMBER) != (attributedStoreId == null))
      throw new ServiceException("会員宛の通知はグループで、受注・ゲスト申請の通知は店舗で作成してください");
    var row = new NotificationDelivery();
    row.attributedStoreId = attributedStoreId;
    row.sourceType = input.sourceType();
    row.sourceId = input.sourceId();
    row.channel = input.channel();
    row.purpose = input.purpose();
    row.subject = input.subject();
    row.body = input.body();
    row.scheduledAt = input.scheduledAt();
    row.dedupeKey = input.dedupeKey();
    row.status = DeliveryStatus.DRAFT;
    return row;
  }

  public DeliveryContent content() {
    return new DeliveryContent(
        sourceType, sourceId, channel, purpose, subject, body, scheduledAt, dedupeKey);
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

  /** 結果不明だけを職員の判定で終端へ移す。判定は試行の一つとして数える。 */
  public void resolve(Long version, DeliveryResolution outcome, String reason) {
    if (!Objects.equals(getVersion(), version) || status != DeliveryStatus.UNKNOWN)
      throw new ConflictException("通知の状態が変わりました。再取得して確認してください");
    this.reason = DeliveryContent.clean(reason, 500);
    status = outcome.status();
    attemptCount++;
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
