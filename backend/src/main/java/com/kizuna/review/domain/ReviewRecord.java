package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_reviews")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewRecord extends StoreScopedEntity {
  @Column(nullable = false, updatable = false, length = 5000)
  private String body;

  @Column(updatable = false, length = 60)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private ReceivedVia receivedVia;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime receivedAt;

  @Column(updatable = false, length = 32)
  private String originOrderId;

  @Column(updatable = false)
  private OffsetDateTime originCheckedAt;

  @Column(updatable = false)
  private Long originOrderVersion;

  @Column(nullable = false, updatable = false)
  private Long recordedBy;

  @Column(nullable = false, updatable = false, length = 255)
  private String recorderName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PermissionStatus permissionStatus;

  @Column(updatable = false, length = 32)
  private String supersedesId;

  @Column(length = 32)
  private String supersededById;

  public static ReviewRecord receive(
      ReviewInput input,
      Long actorId,
      String actorName,
      OffsetDateTime originCheckedAt,
      Long originVersion) {
    return builder()
        .body(input.body())
        .displayName(input.displayName())
        .receivedVia(input.receivedVia())
        .receivedAt(input.receivedAt())
        .originOrderId(input.originOrderId())
        .originCheckedAt(originCheckedAt)
        .originOrderVersion(originVersion)
        .recordedBy(actorId)
        .recorderName(actorName)
        .status(Status.PENDING)
        .permissionStatus(PermissionStatus.NOT_GRANTED)
        .build();
  }

  public static ReviewRecord correction(
      ReviewRecord previous,
      ReviewInput input,
      Long actorId,
      String actorName,
      OffsetDateTime originCheckedAt,
      Long originVersion) {
    previous.requireNoSuccessor();
    var row = receive(input, actorId, actorName, originCheckedAt, originVersion);
    row.supersedesId = previous.getId();
    return row;
  }

  public void linkCorrection(String nextId) {
    requireNoSuccessor();
    status = Status.WITHDRAWN;
    supersededById = nextId;
  }

  private void requireNoSuccessor() {
    if (supersededById != null) throw new ConflictException("この口コミには既に訂正先があります");
  }

  public void requireVersion(Long expected) {
    if (!getVersion().equals(expected)) throw new ConflictException("口コミが更新されています。最新の内容を確認してください");
  }

  public void approve() {
    requirePending();
    status = Status.APPROVED;
  }

  public void reject() {
    requirePending();
    status = Status.REJECTED;
  }

  public void withdraw() {
    if (status == Status.WITHDRAWN) throw new ConflictException("この口コミは既に取り下げられています");
    status = Status.WITHDRAWN;
  }

  public void grantPermission() {
    if (permissionStatus != PermissionStatus.NOT_GRANTED
        || (status != Status.PENDING && status != Status.APPROVED))
      throw new ConflictException("この口コミには公開許可を記録できません");
    permissionStatus = PermissionStatus.GRANTED;
  }

  public void revokePermission() {
    if (permissionStatus != PermissionStatus.GRANTED) throw new ConflictException("有効な公開許可がありません");
    permissionStatus = PermissionStatus.REVOKED;
  }

  private void requirePending() {
    if (status != Status.PENDING) throw new ConflictException("審査待ちの口コミだけを審査できます");
  }
}
