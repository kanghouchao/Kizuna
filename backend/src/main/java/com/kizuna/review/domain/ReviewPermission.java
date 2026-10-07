package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.Basis;
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
@Table(name = "t_review_permissions")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewPermission extends StoreScopedEntity {
  @Column(updatable = false, nullable = false, length = 32)
  private String reviewId;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false, nullable = false, length = 24)
  private Basis basisType;

  @Column(updatable = false, nullable = false)
  private OffsetDateTime grantedAt;

  @Column(updatable = false, nullable = false, length = 500)
  private String evidenceNote;

  @Column(updatable = false, nullable = false)
  private Long recordedBy;

  @Column(updatable = false, nullable = false, length = 255)
  private String recorderName;

  @Column(updatable = true)
  private OffsetDateTime withdrawalReceivedAt;

  @Column(updatable = true, length = 500)
  private String revocationReason;

  @Column(updatable = true)
  private Long revokedBy;

  @Column(updatable = true, length = 255)
  private String revokerName;

  @Column(updatable = true)
  private OffsetDateTime revokedAt;

  public static ReviewPermission grant(
      String reviewId,
      Basis basis,
      OffsetDateTime at,
      String evidence,
      Long actorId,
      String actorName) {
    return builder()
        .reviewId(reviewId)
        .basisType(basis)
        .grantedAt(at)
        .evidenceNote(evidence)
        .recordedBy(actorId)
        .recorderName(actorName)
        .build();
  }

  public void revoke(
      OffsetDateTime receivedAt,
      String reason,
      Long actorId,
      String actorName,
      OffsetDateTime now) {
    if (revokedAt != null) throw new ConflictException("公開許可は既に撤回されています");
    withdrawalReceivedAt = receivedAt;
    revocationReason = reason;
    revokedBy = actorId;
    revokerName = actorName;
    revokedAt = now;
  }
}
