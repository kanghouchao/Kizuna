package com.kizuna.notificationdelivery.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_notification_attempts")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAttempt extends StoreScopedEntity {
  @Column(nullable = false, updatable = false, length = 32)
  private String deliveryId;

  @Column(nullable = false, updatable = false)
  private int attemptNumber;

  @Column(nullable = false, updatable = false)
  private Long taskExecutionId;

  @Column(nullable = false, updatable = false)
  private Long serviceUserId;

  @Column(nullable = false, updatable = false, length = 255)
  private String serviceName;

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
    attempt.serviceUserId = serviceId;
    attempt.serviceName = serviceName;
    attempt.reason = row.getReason();
    attempt.startedAt = now;
    attempt.status = DeliveryStatus.DISPATCHED;
    return attempt;
  }

  public void sending() {
    if (status != DeliveryStatus.DISPATCHED) throw new ConflictException("この試行は開始できません");
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
