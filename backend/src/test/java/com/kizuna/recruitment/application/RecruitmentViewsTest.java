package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantInterview;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantSummaryView;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.ReceptionChannel;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecruitmentViewsTest {
  static final MediumReference MEDIUM = new MediumReference("m", "求人誌", true);

  // 同じ型の項目が並ぶので、各項目を同名の getter と比べて取り違えを検出する。
  static void copiesGetters(Record view, Object source, Map<String, String> renamed) {
    for (RecordComponent component : view.getClass().getRecordComponents()) {
      if (component.getName().equals("medium")) continue;
      String property = renamed.getOrDefault(component.getName(), component.getName());
      assertThat(invoke(view, component.getName()))
          .as(component.getName())
          .isNotNull()
          .isEqualTo(
              invoke(
                  source,
                  "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1)));
    }
  }

  static Object invoke(Object target, String method) {
    return ReflectionTestUtils.invokeMethod(target, method);
  }

  static Applicant applicant() {
    var applicant = new Applicant();
    applicant.setId("a");
    applicant.setStoreId(1L);
    applicant.setCreatedAt(OffsetDateTime.parse("2026-11-01T10:00:00+09:00"));
    applicant.setUpdatedAt(OffsetDateTime.parse("2026-11-02T10:00:00+09:00"));
    var values =
        Map.<String, Object>ofEntries(
            Map.entry("name", "応募者"),
            Map.entry("channel", ReceptionChannel.PHONE),
            Map.entry("sourceType", ApplicantSourceType.MEDIA),
            Map.entry("mediumId", "m"),
            Map.entry("referrer", "紹介者"),
            Map.entry("assignee", "担当"),
            Map.entry("phone", "000"),
            Map.entry("email", "a@example.com"),
            Map.entry("address", "住所"),
            Map.entry("experience", "経歴"),
            Map.entry("desiredConditions", "希望"),
            Map.entry("status", ApplicantStatus.INTERVIEWED),
            Map.entry(
                "interview",
                new ApplicantInterview(
                    OffsetDateTime.parse("2026-11-01T12:00:00+09:00"),
                    "面接者",
                    "メモ",
                    Map.of("確認", true))),
            Map.entry("modifiedBy", 9L),
            Map.entry("enrollmentId", "e1"),
            Map.entry("anonymizedAt", OffsetDateTime.parse("2026-11-03T10:00:00+09:00")),
            Map.entry("version", 3L));
    values.forEach((field, value) -> ReflectionTestUtils.setField(applicant, field, value));
    return applicant;
  }

  @Test
  void applicantDetailCopiesEachColumnUnderItsOwnName() {
    var applicant = applicant();
    var detail = ApplicantViews.Detail.of(applicant, MEDIUM);
    copiesGetters(detail, applicant, Map.of());
    assertThat(detail.medium()).isEqualTo(MEDIUM);
  }

  @Test
  void applicantSummaryCopiesEachProjectedColumnUnderItsOwnName() {
    var applicant = applicant();
    var projection =
        (ApplicantSummaryView)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {ApplicantSummaryView.class},
                (proxy, method, args) -> invoke(applicant, method.getName()));
    var summary = ApplicantViews.Summary.of(projection, MEDIUM);
    copiesGetters(summary, applicant, Map.of());
    assertThat(summary.medium()).isEqualTo(MEDIUM);
  }

  @Test
  void attachmentViewsCopyEachColumnUnderItsOwnName() {
    var upload =
        AttachmentUpload.reserve(
            1L, "a", UUID.randomUUID(), "a".repeat(64), "image/png", "b".repeat(64), 7, "v1", 9L);
    upload.setId("u");
    upload.setCreatedAt(OffsetDateTime.parse("2026-11-01T10:00:00+09:00"));
    upload.recordFailure(AttachmentUpload.Failure.CONTENT_MISMATCH);
    copiesGetters(AttachmentViews.Summary.of(upload), upload, Map.of());
    copiesGetters(AttachmentViews.Upload.of(upload), upload, Map.of("failureCode", "failure"));
  }
}
