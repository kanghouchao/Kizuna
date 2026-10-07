package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_review_history")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewHistory extends StoreScopedEntity {
  @Column(updatable = false, nullable = false, length = 32)
  private String reviewId;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false, nullable = false, length = 32)
  private Operation type;

  @Column(updatable = false, nullable = false)
  private Long actorId;

  @Column(updatable = false, nullable = false, length = 255)
  private String actorName;

  @Column(updatable = false)
  private Long beforeVersion;

  @Column(updatable = false, nullable = false)
  private Long afterVersion;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false, length = 20)
  private Status beforeStatus;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false, nullable = false, length = 20)
  private Status afterStatus;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false, length = 20)
  private PermissionStatus beforePermissionStatus;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false, nullable = false, length = 20)
  private PermissionStatus afterPermissionStatus;

  @Column(updatable = false, length = 500)
  private String reason;

  @Column(updatable = false, length = 32)
  private String relatedReviewId;

  @Column(updatable = false, length = 32)
  private String permissionRecordId;

  public record Before(Long version, Status status, PermissionStatus permissionStatus) {
    public static Before of(ReviewRecord r) {
      return new Before(r.getVersion(), r.getStatus(), r.getPermissionStatus());
    }
  }

  public static ReviewHistory record(
      ReviewRecord r,
      Operation type,
      Long actorId,
      String actorName,
      Before before,
      String reason,
      String relatedId,
      String permissionId) {
    return builder()
        .reviewId(r.getId())
        .type(type)
        .actorId(actorId)
        .actorName(actorName)
        .beforeVersion(before == null ? null : before.version())
        .beforeStatus(before == null ? null : before.status())
        .beforePermissionStatus(before == null ? null : before.permissionStatus())
        .afterVersion(r.getVersion())
        .afterStatus(r.getStatus())
        .afterPermissionStatus(r.getPermissionStatus())
        .reason(reason)
        .relatedReviewId(relatedId)
        .permissionRecordId(permissionId)
        .build();
  }
}
