package com.kizuna.survey.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.survey.domain.SurveyValues.Operation;
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
@Table(name = "t_survey_operations")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyOperation extends StoreScopedEntity {

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false, length = 120)
  private String dedupeKey;

  @Column(nullable = false, updatable = false, length = 64)
  private String fingerprint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 32)
  private Operation type;

  @Column(updatable = false, length = 32)
  private String revisionId;

  @Column(updatable = false, length = 32)
  private String responseId;

  @Column(nullable = false, updatable = false)
  private Long committedVersion;

  public static SurveyOperation record(
      Long actor,
      String key,
      String hash,
      Operation type,
      StoreScopedEntity result,
      boolean answer) {
    return builder()
        .actorId(actor)
        .dedupeKey(key)
        .fingerprint(hash)
        .type(type)
        .revisionId(answer ? null : result.getId())
        .responseId(answer ? result.getId() : null)
        .committedVersion(result.getVersion())
        .build();
  }

  public void requireSame(String hash) {
    if (!fingerprint.equals(hash)) throw new ConflictException("同じ要求キーに異なる操作が指定されています");
  }
}
