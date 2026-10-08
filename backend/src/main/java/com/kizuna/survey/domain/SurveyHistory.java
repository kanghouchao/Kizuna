package com.kizuna.survey.domain;

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
@Table(name = "t_survey_history")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyHistory extends StoreScopedEntity {

  @Column(updatable = false, length = 32)
  private String revisionId;

  @Column(updatable = false, length = 32)
  private String responseId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 32)
  private Operation type;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false, length = 255)
  private String actorName;

  @Column(updatable = false)
  private Long beforeVersion;

  @Column(nullable = false, updatable = false)
  private Long afterVersion;

  @Column(updatable = false, length = 20)
  private String beforeStatus;

  @Column(nullable = false, updatable = false, length = 20)
  private String afterStatus;

  @Column(updatable = false, length = 500)
  private String reason;

  @Column(updatable = false, length = 32)
  private String relatedResponseId;

  public record Before(Long version, String status) {}

  public static SurveyHistory revision(
      SurveyRevision row, Operation type, Long actor, String name, Before before, String reason) {
    return create(row, false, row.getStatus().name(), type, actor, name, before, reason, null);
  }

  public static SurveyHistory answer(
      SurveyAnswer row,
      Operation type,
      Long actor,
      String name,
      Before before,
      String reason,
      String related) {
    return create(row, true, row.getStatus().name(), type, actor, name, before, reason, related);
  }

  private static SurveyHistory create(
      StoreScopedEntity row,
      boolean answer,
      String status,
      Operation type,
      Long actor,
      String name,
      Before before,
      String reason,
      String related) {
    return builder()
        .revisionId(answer ? null : row.getId())
        .responseId(answer ? row.getId() : null)
        .type(type)
        .actorId(actor)
        .actorName(name)
        .beforeVersion(before == null ? null : before.version())
        .beforeStatus(before == null ? null : before.status())
        .afterVersion(row.getVersion())
        .afterStatus(status)
        .reason(reason)
        .relatedResponseId(related)
        .build();
  }
}
