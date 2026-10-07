package com.kizuna.survey.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.survey.domain.SurveyValues.AnswerStatus;
import com.kizuna.survey.domain.SurveyValues.ReceivedVia;
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
@Table(name = "t_survey_responses")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyAnswer extends StoreScopedEntity {

  @Column(nullable = false, updatable = false, length = 32)
  private String surveyId;

  @Column(nullable = false, updatable = false, length = 32)
  private String revisionId;

  @Column(nullable = false, updatable = false)
  private Integer revisionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 24)
  private ReceivedVia receivedVia;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime receivedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<SurveyAnswers.Answer> answers;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AnswerStatus status;

  @Column(nullable = false, updatable = false)
  private Long recordedBy;

  @Column(nullable = false, updatable = false, length = 255)
  private String recorderName;

  @Column(updatable = false, length = 32)
  private String supersedesId;

  @Column(length = 32)
  private String supersededById;

  public static SurveyAnswer receive(
      SurveyRevision revision, SurveyAnswers input, Long actor, String name, String previous) {
    return builder()
        .surveyId(revision.getSurveyId())
        .revisionId(revision.getId())
        .revisionNumber(revision.getRevisionNumber())
        .receivedVia(input.receivedVia())
        .receivedAt(input.receivedAt())
        .answers(input.answers())
        .status(AnswerStatus.ACTIVE)
        .recordedBy(actor)
        .recorderName(name)
        .supersedesId(previous)
        .build();
  }

  public void requireVersion(Long version) {
    if (!getVersion().equals(version)) throw new ConflictException("回答が更新されています。最新の内容を確認してください");
  }

  public void requireNoSuccessor() {
    if (supersededById != null) throw new ConflictException("この回答には既に訂正先があります");
  }

  public void withdraw() {
    if (status == AnswerStatus.WITHDRAWN) throw new ConflictException("この回答は既に取り下げられています");
    status = AnswerStatus.WITHDRAWN;
  }

  public void correctTo(String id) {
    requireNoSuccessor();
    status = AnswerStatus.WITHDRAWN;
    supersededById = id;
  }
}
