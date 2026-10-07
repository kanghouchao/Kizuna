package com.kizuna.recruitment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicantTest {
  private static ApplicantIntake intake() {
    return new ApplicantIntake(
        "応募者",
        ReceptionChannel.WEB,
        ApplicantSourceType.DIRECT,
        null,
        null,
        "担当者",
        "000",
        null,
        "非公開住所",
        "経歴",
        "希望");
  }

  private static ApplicantInterview interview() {
    return new ApplicantInterview(
        OffsetDateTime.parse("2026-10-01T10:00:00+09:00"), "面接担当", "非公開メモ", Map.of("希望確認", true));
  }

  @Test
  void progressesWithInterviewAndReasonAndWithdrawalIsTerminal() {
    var applicant = Applicant.receive(intake());
    assertThat(applicant.getStatus()).isEqualTo(ApplicantStatus.RECEIVED);
    applicant.transition(ApplicantStatus.SCREENING, "確認開始");
    assertThatThrownBy(() -> applicant.transition(ApplicantStatus.INTERVIEWED, "面接終了"))
        .isInstanceOf(ServiceException.class);
    applicant.recordInterview(interview());
    applicant.transition(ApplicantStatus.INTERVIEWED, "面接終了");
    applicant.transition(ApplicantStatus.SCREENING, "追加確認");
    applicant.transition(ApplicantStatus.WITHDRAWN, "本人の辞退");
    assertThatThrownBy(() -> applicant.replaceIntake(intake()))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> applicant.recordInterview(interview()))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> applicant.transition(ApplicantStatus.SCREENING, "再開"))
        .isInstanceOf(ConflictException.class);
  }

  @ParameterizedTest
  @EnumSource(ApplicantStatus.class)
  void enforcesEveryTransitionEdge(ApplicantStatus source) {
    for (ApplicantStatus target : ApplicantStatus.values()) {
      var applicant = Applicant.receive(intake());
      applicant.recordInterview(interview());
      ReflectionTestUtils.setField(applicant, "status", source);
      boolean allowed =
          !source.isTerminal()
              && (target == ApplicantStatus.WITHDRAWN
                  || source == ApplicantStatus.RECEIVED && target == ApplicantStatus.SCREENING
                  || source == ApplicantStatus.SCREENING && target == ApplicantStatus.INTERVIEWED
                  || source == ApplicantStatus.INTERVIEWED && target == ApplicantStatus.SCREENING);
      if (allowed) {
        applicant.transition(target, "変更理由");
        assertThat(applicant.getStatus()).isEqualTo(target);
      } else {
        assertThatThrownBy(() -> applicant.transition(target, "変更理由"))
            .isInstanceOfAny(ServiceException.class, ConflictException.class);
        assertThat(applicant.getStatus()).isEqualTo(source);
      }
    }
  }

  @Test
  void rejectsStaleVersionAndTracksEverySaveEvenUnchanged() {
    var applicant = Applicant.receive(intake());
    ReflectionTestUtils.setField(applicant, "version", 2L);
    applicant.requireVersion(2L);
    assertThatThrownBy(() -> applicant.requireVersion(1L)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> applicant.requireVersion(null)).isInstanceOf(ConflictException.class);
    applicant.recordEditor(10L);
    applicant.recordEditor(11L);
    assertThat(applicant.getEditSequence()).isEqualTo(2);
    assertThat(applicant.getModifiedBy()).isEqualTo(11L);
    assertThatThrownBy(() -> applicant.transition(ApplicantStatus.SCREENING, " "))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void keepsChannelIndependentFromMediaAndReferral() {
    var media =
        new ApplicantIntake(
            "応募者",
            ReceptionChannel.PHONE,
            ApplicantSourceType.MEDIA,
            "媒体A",
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(media.channel()).isEqualTo(ReceptionChannel.PHONE);
    assertThat(media.sourceMedia()).isEqualTo("媒体A");
    for (ApplicantSourceType type : ApplicantSourceType.values()) {
      assertThatThrownBy(
              () ->
                  new ApplicantIntake(
                      "応募者",
                      ReceptionChannel.WEB,
                      type,
                      "媒体",
                      "紹介者",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(ServiceException.class);
    }
    assertThatThrownBy(
            () ->
                new ApplicantIntake(
                    " ",
                    ReceptionChannel.WEB,
                    ApplicantSourceType.DIRECT,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> ApplicantIntake.text("x".repeat(101), 100))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> ApplicantIntake.text("x\0", 100)).isInstanceOf(ServiceException.class);
    assertThat(ApplicantIntake.text("   ", 100)).isNull();
  }

  @Test
  void blankEmailNormalizesToNullAndMalformedEmailIsRejected() {
    var blank =
        new ApplicantIntake(
            "応募者",
            ReceptionChannel.WEB,
            ApplicantSourceType.DIRECT,
            null,
            null,
            null,
            null,
            "   ",
            null,
            null,
            null);
    assertThat(blank.email()).isNull();
    assertThatThrownBy(
            () ->
                new ApplicantIntake(
                    "応募者",
                    ReceptionChannel.WEB,
                    ApplicantSourceType.DIRECT,
                    null,
                    null,
                    null,
                    null,
                    "not-an-address",
                    null,
                    null,
                    null))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void checklistIsBoundedAndImmutable() {
    Map<String, Boolean> values = new HashMap<>();
    values.put("希望確認", true);
    var interview = new ApplicantInterview(OffsetDateTime.now(), "担当", null, values);
    values.put("後書き", false);
    assertThat(interview.checklist()).containsOnlyKeys("希望確認");
    for (String key :
        new String[] {"", "__proto__", "constructor", "prototype", "x".repeat(101), "x\0"}) {
      assertThatThrownBy(
              () -> new ApplicantInterview(OffsetDateTime.now(), "担当", null, Map.of(key, true)))
          .isInstanceOf(ServiceException.class);
    }
    for (int i = 0; i < 31; i++) values.put("item" + i, true);
    assertThatThrownBy(() -> new ApplicantInterview(OffsetDateTime.now(), "担当", null, values))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> new ApplicantInterview(null, "担当", null, Map.of()))
        .isInstanceOf(ServiceException.class);
  }
}
