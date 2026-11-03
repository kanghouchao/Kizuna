package com.kizuna.survey.application;

import com.kizuna.receipt.once.Receipt;
import com.kizuna.survey.domain.SurveyAnswer;
import com.kizuna.survey.domain.SurveyRevision;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import java.time.OffsetDateTime;

public final class SurveyViews {
  private SurveyViews() {}

  /** 最新版と、受付中・下書きの版を引き当てたアンケートの要約。 */
  public record Summary(
      String id,
      String latestRevisionId,
      int latestRevisionNumber,
      String latestTitle,
      RevisionStatus latestStatus,
      String openRevisionId,
      String draftRevisionId,
      OffsetDateTime createdAt) {}

  public record Detail(Summary summary, Long recordedBy, String recorderName) {}

  /** 書き込みの受領と、その対象の現在の行。 */
  public sealed interface Written {
    Receipt receipt();
  }

  public record RevisionWritten(SurveyRevision revision, Receipt receipt) implements Written {}

  public record AnswerWritten(SurveyAnswer answer, Receipt receipt) implements Written {}
}
