package com.kizuna.recruitment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.recruitment.application.ApplicantViews;
import com.kizuna.recruitment.application.AttachmentViews;
import com.kizuna.recruitment.domain.ApplicantInterview;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.ReceptionChannel;
import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecruitmentResponsesTest {
  static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-11-01T10:00:00+09:00");
  static final OffsetDateTime UPDATED = OffsetDateTime.parse("2026-11-02T10:00:00+09:00");
  static final OffsetDateTime ANONYMIZED = OffsetDateTime.parse("2026-11-03T10:00:00+09:00");
  static final MediumReference MEDIUM = new MediumReference("m", "求人誌", false);

  // 同じ型の項目が並ぶので、名前の一致する項目どうしで値を比べて取り違えを検出する。媒体だけは型が変わる。
  static void sameByName(Record source, Record target) {
    for (RecordComponent component : target.getClass().getRecordComponents()) {
      if (component.getName().equals("medium")) continue;
      assertThat(value(target, component.getName()))
          .as(component.getName())
          .isEqualTo(value(source, component.getName()));
    }
    assertThat(names(target)).isSubsetOf(names(source));
  }

  static List<String> names(Record value) {
    return Arrays.stream(value.getClass().getRecordComponents())
        .map(RecordComponent::getName)
        .toList();
  }

  static Object value(Record record, String name) {
    try {
      return record.getClass().getMethod(name).invoke(record);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }

  @Test
  void applicantDetailCopiesEveryFieldUnderItsOwnName() {
    var detail =
        new ApplicantViews.Detail(
            "a",
            "応募者",
            ApplicantStatus.INTERVIEWED,
            ReceptionChannel.PHONE,
            ApplicantSourceType.MEDIA,
            MEDIUM,
            "担当",
            CREATED,
            3L,
            "紹介者",
            "000",
            "a@example.com",
            "住所",
            "経歴",
            "希望",
            new ApplicantInterview(CREATED, "面接者", "メモ", Map.of("確認", true)),
            9L,
            UPDATED,
            "e1",
            ANONYMIZED);
    var response = ApplicantResponse.from(detail);
    sameByName(detail, response);
    assertThat(response.medium()).isEqualTo(new ApplicantMediumResponse("m", "求人誌"));
    assertThat(ApplicantResponse.from(withoutMedium(detail)).medium()).isNull();
  }

  static ApplicantViews.Detail withoutMedium(ApplicantViews.Detail d) {
    return new ApplicantViews.Detail(
        d.id(),
        d.name(),
        d.status(),
        d.channel(),
        d.sourceType(),
        null,
        d.assignee(),
        d.createdAt(),
        d.version(),
        d.referrer(),
        d.phone(),
        d.email(),
        d.address(),
        d.experience(),
        d.desiredConditions(),
        d.interview(),
        d.modifiedBy(),
        d.updatedAt(),
        d.enrollmentId(),
        d.anonymizedAt());
  }

  @Test
  void applicantSummaryCopiesEveryFieldUnderItsOwnName() {
    var summary =
        new ApplicantViews.Summary(
            "a",
            "応募者",
            ApplicantStatus.SCREENING,
            ReceptionChannel.WEB,
            ApplicantSourceType.MEDIA,
            MEDIUM,
            "担当",
            CREATED,
            2L,
            "e1",
            ANONYMIZED);
    var response = ApplicantSummaryResponse.from(summary);
    sameByName(summary, response);
    assertThat(response.medium()).isEqualTo(new ApplicantMediumResponse("m", "求人誌"));
  }

  @Test
  void historyCopiesEveryColumn() {
    var history =
        ApplicantStatusHistory.builder()
            .applicantId("a")
            .actorId(9L)
            .previousStatus(ApplicantStatus.RECEIVED)
            .newStatus(ApplicantStatus.SCREENING)
            .reason("選考開始")
            .build();
    history.setId("h");
    history.setCreatedAt(CREATED);
    assertThat(ApplicantHistoryResponse.from(history))
        .isEqualTo(
            new ApplicantHistoryResponse(
                "h", ApplicantStatus.RECEIVED, ApplicantStatus.SCREENING, 9L, CREATED, "選考開始"));
  }

  @Test
  void attachmentViewsCopyEveryFieldUnderItsOwnName() {
    var policy =
        new AttachmentViews.Policy(true, List.of("image/jpeg", "image/png"), 1, 2, 3, 4, 5, 6);
    sameByName(policy, AttachmentPolicyResponse.from(policy));
    var summary = new AttachmentViews.Summary("x", "image/png", 7, CREATED);
    sameByName(summary, AttachmentSummaryResponse.from(summary));
    var upload =
        new AttachmentViews.Upload(
            "u",
            UUID.randomUUID(),
            AttachmentUpload.Status.RECOVERY_REQUIRED,
            "image/jpeg",
            8,
            CREATED,
            AttachmentUpload.Failure.CONTENT_MISMATCH);
    sameByName(upload, AttachmentUploadResponse.from(upload));
  }
}
