package com.kizuna.survey.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "t_survey_revisions")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyRevision extends StoreScopedEntity {

  @Column(nullable = false, updatable = false, length = 32)
  private String surveyId;

  @Column(nullable = false, updatable = false)
  private Integer revisionNumber;

  @Column(updatable = false, length = 32)
  private String basedOnRevisionId;

  @Column(nullable = false, length = 120)
  private String title;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<SurveyDefinition.Question> questions;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RevisionStatus status;

  @Column(nullable = false, updatable = false)
  private Long recordedBy;

  @Column(nullable = false, updatable = false, length = 255)
  private String recorderName;

  private OffsetDateTime openedAt;
  private OffsetDateTime closedAt;

  public static SurveyRevision draft(
      String surveyId,
      int number,
      String basedOn,
      SurveyDefinition definition,
      Long actor,
      String name) {
    return builder()
        .surveyId(surveyId)
        .revisionNumber(number)
        .basedOnRevisionId(basedOn)
        .title(definition.title())
        .questions(definition.questions())
        .status(RevisionStatus.DRAFT)
        .recordedBy(actor)
        .recorderName(name)
        .build();
  }

  public void requireVersion(Long expected) {
    if (!getVersion().equals(expected)) throw new ConflictException("設問版が更新されています。最新の内容を確認してください");
  }

  public void replace(SurveyDefinition definition) {
    require(RevisionStatus.DRAFT);
    title = definition.title();
    questions = definition.questions();
    setUpdatedAt(OffsetDateTime.now());
  }

  public void open(OffsetDateTime now) {
    require(RevisionStatus.DRAFT);
    status = RevisionStatus.OPEN;
    openedAt = now;
  }

  public void close(OffsetDateTime now) {
    if (status == RevisionStatus.CLOSED) throw new ConflictException("この設問版は既に終了しています");
    status = RevisionStatus.CLOSED;
    closedAt = now;
  }

  public void require(RevisionStatus expected) {
    if (status != expected) throw new ConflictException("設問版の状態が変わっています。最新の内容を確認してください");
  }
}
