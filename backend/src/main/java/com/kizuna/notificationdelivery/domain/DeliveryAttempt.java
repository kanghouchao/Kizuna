package com.kizuna.notificationdelivery.domain;

import com.kizuna.shared.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_notification_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAttempt extends DeliveryRecord {
  public static final String MANUAL_RESOLUTION = "MANUAL_RESOLUTION";

  @Column(nullable = false, updatable = false, length = 32)
  private String deliveryId;

  @Column(nullable = false, updatable = false)
  private int attemptNumber;

  @Column(updatable = false)
  private Long taskExecutionId;

  @Column(nullable = false, updatable = false)
  private Long actorUserId;

  @Column(nullable = false, length = 255)
  private String actorName;

  @Column(nullable = false, updatable = false, length = 500)
  private String reason;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime startedAt;

  private OffsetDateTime finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DeliveryStatus status;

  @Column(length = 40)
  private String failureCode;

  public static DeliveryAttempt dispatch(
      NotificationDelivery row,
      Long executionId,
      Long serviceId,
      String serviceName,
      OffsetDateTime now) {
    var attempt = new DeliveryAttempt();
    attempt.deliveryId = row.getId();
    attempt.attemptNumber = row.getAttemptCount();
    attempt.taskExecutionId = executionId;
    attempt.actorUserId = serviceId;
    attempt.actorName = serviceName;
    attempt.reason = row.getReason();
    attempt.startedAt = now;
    attempt.status = DeliveryStatus.DISPATCHED;
    return attempt;
  }

  /** 職員の判定は定期処理の実行を持たない試行として残し、失敗コードで提供元の結果と区別する。 */
  public static DeliveryAttempt resolution(
      NotificationDelivery row, Long staffId, String staffName, OffsetDateTime now) {
    var attempt = new DeliveryAttempt();
    attempt.deliveryId = row.getId();
    attempt.attemptNumber = row.getAttemptCount();
    attempt.actorUserId = staffId;
    attempt.actorName = staffName;
    attempt.reason = row.getReason();
    attempt.startedAt = now;
    attempt.finishedAt = now;
    attempt.status = row.getStatus();
    attempt.failureCode = MANUAL_RESOLUTION;
    return attempt;
  }

  public void sending(String serviceName) {
    if (status != DeliveryStatus.DISPATCHED) throw new ConflictException("この試行は開始できません");
    this.actorName = serviceName;
    status = DeliveryStatus.SENDING;
  }

  public void finish(DeliveryStatus result, String code, OffsetDateTime now) {
    if (status != DeliveryStatus.DISPATCHED && status != DeliveryStatus.SENDING)
      throw new ConflictException("この試行の結果は変更できません");
    status = result;
    failureCode = code;
    finishedAt = now;
  }
}
