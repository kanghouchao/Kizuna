package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.shared.exception.ConflictException;
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
@Table(name = "t_review_operations")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewOperation extends StoreScopedEntity {
  @Column(updatable = false, nullable = false)
  private Long actorId;

  @Column(updatable = false, nullable = false, length = 120)
  private String dedupeKey;

  @Column(updatable = false, nullable = false, length = 64)
  private String fingerprint;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false, nullable = false, length = 32)
  private Operation type;

  @Column(updatable = false, nullable = false, length = 32)
  private String reviewId;

  @Column(updatable = false, nullable = false)
  private Long committedVersion;

  public static ReviewOperation record(
      Long actorId, String key, String hash, Operation type, ReviewRecord result) {
    return builder()
        .actorId(actorId)
        .dedupeKey(key)
        .fingerprint(hash)
        .type(type)
        .reviewId(result.getId())
        .committedVersion(result.getVersion())
        .build();
  }

  public void requireSame(String hash) {
    if (!fingerprint.equals(hash)) throw new ConflictException("同じ要求キーに異なる操作が指定されています");
  }
}
