package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.cast.application.CastCreateCommand;
import com.kizuna.cast.application.CastCreation;
import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.recruitment.api.dto.ApplicantEnrollmentRequest;
import com.kizuna.recruitment.api.dto.ApplicantIntakeRequest;
import com.kizuna.recruitment.api.dto.ApplicantInterviewRequest;
import com.kizuna.recruitment.api.dto.ApplicantMapper;
import com.kizuna.recruitment.api.dto.ApplicantTransitionRequest;
import com.kizuna.recruitment.api.dto.ApplicantUpdateRequest;
import com.kizuna.recruitment.api.dto.RecruitmentPolicyResponse;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import com.kizuna.recruitment.domain.ApplicantStatusHistoryRepository;
import com.kizuna.recruitment.domain.ReceptionChannel;
import com.kizuna.settings.application.ApplicantRetentionSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicantServiceTest {
  private static final Long STORE_ID = 7L;
  @Mock ApplicantRepository applicants;
  @Mock ApplicantStatusHistoryRepository histories;
  @Mock UserRepository users;
  @Mock BusinessAudit audit;
  @Mock SystemConfigService systemConfigs;
  @Mock CastService casts;
  ApplicantService service;
  Applicant applicant;
  ApplicantIntakeRequest intake =
      new ApplicantIntakeRequest(
          "応募者",
          ReceptionChannel.PHONE,
          ApplicantSourceType.MEDIA,
          "媒体",
          null,
          "担当",
          "000",
          null,
          "住所",
          "経歴",
          "希望");

  @BeforeEach
  void setup() {
    service =
        new ApplicantService(
            applicants,
            histories,
            users,
            Mappers.getMapper(ApplicantMapper.class),
            audit,
            systemConfigs,
            casts);
    applicant = Applicant.receive(STORE_ID, intake.toIntake());
    applicant.setId("a");
    ReflectionTestUtils.setField(applicant, "version", 0L);
  }

  void actor() {
    User user = new User();
    user.setId(9L);
    when(users.findByEmail("actor")).thenReturn(Optional.of(user));
  }

  void lock() {
    when(applicants.findByIdAndStoreIdForUpdate("a", STORE_ID)).thenReturn(Optional.of(applicant));
  }

  @Test
  void policyReturnsEachTerminalStatusRetention() {
    when(systemConfigs.applicantRetentionSettings())
        .thenReturn(new ApplicantRetentionSettings(6, 12, 36));

    assertThat(service.policy().retentionPeriods())
        .isEqualTo(new RecruitmentPolicyResponse.RetentionPeriods(6, 12, 36));
  }

  @Test
  void createsPrivateApplicantAndInitialHistory() {
    actor();
    when(applicants.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              Applicant value = call.getArgument(0);
              value.setId("a");
              ReflectionTestUtils.setField(value, "version", 0L);
              return value;
            });
    var response = service.create(STORE_ID, intake, "actor");
    assertThat(response.status()).isEqualTo(ApplicantStatus.RECEIVED);
    assertThat(response.address()).isEqualTo("住所");
    assertThat(response.modifiedBy()).isEqualTo(9L);
    verify(audit)
        .recordCurrent(
            STORE_ID,
            "APPLICANT_RECEIVED",
            "APPLICANT",
            "a",
            Map.of(),
            Map.of("status", "RECEIVED", "version", "0"));
    var captor = ArgumentCaptor.forClass(ApplicantStatusHistory.class);
    verify(histories).save(captor.capture());
    assertThat(captor.getValue().getPreviousStatus()).isNull();
    assertThat(captor.getValue().getActorId()).isEqualTo(9L);
    assertThat(captor.getValue().getStoreId()).isEqualTo(STORE_ID);
  }

  @Test
  void editsEvenIdenticalIntakeAndInterviewUnderVersionCheck() {
    actor();
    lock();
    service.update(STORE_ID, "a", new ApplicantUpdateRequest(0L, intake), "actor");
    service.interview(
        STORE_ID,
        "a",
        new ApplicantInterviewRequest(0L, OffsetDateTime.now(), "担当", "私用メモ", Map.of("確認", true)),
        "actor");
    assertThat(applicant.getEditSequence()).isEqualTo(2);
    assertThat(applicant.getInterview().notes()).isEqualTo("私用メモ");
    var safe = Map.of("status", "RECEIVED", "version", "0");
    verify(audit).recordCurrent(STORE_ID, "APPLICANT_INTAKE_UPDATED", "APPLICANT", "a", safe, safe);
    verify(audit)
        .recordCurrent(STORE_ID, "APPLICANT_INTERVIEW_UPDATED", "APPLICANT", "a", safe, safe);
  }

  @Test
  void transitionRecordsReasonActorAndPreviousState() {
    actor();
    lock();
    service.transition(
        STORE_ID,
        "a",
        new ApplicantTransitionRequest(0L, ApplicantStatus.SCREENING, "  選考開始  "),
        "actor");
    var captor = ArgumentCaptor.forClass(ApplicantStatusHistory.class);
    verify(histories).save(captor.capture());
    var history = captor.getValue();
    assertThat(history.getPreviousStatus()).isEqualTo(ApplicantStatus.RECEIVED);
    assertThat(history.getNewStatus()).isEqualTo(ApplicantStatus.SCREENING);
    assertThat(history.getReason()).isEqualTo("選考開始");
    assertThat(history.getActorId()).isEqualTo(9L);
    assertThat(history.getStoreId()).isEqualTo(STORE_ID);
    verify(audit)
        .recordCurrent(
            STORE_ID,
            "APPLICANT_STATUS_CHANGED",
            "APPLICANT",
            "a",
            Map.of("status", "RECEIVED", "version", "0"),
            Map.of("status", "SCREENING", "version", "0"));
  }

  @Test
  void rejectsStaleBeforeAnyMutation() {
    lock();
    assertThatThrownBy(
            () -> service.update(STORE_ID, "a", new ApplicantUpdateRequest(3L, intake), "actor"))
        .isInstanceOf(ConflictException.class);
    assertThat(applicant.getEditSequence()).isZero();
    verifyNoInteractions(audit);
    verify(applicants, never()).flush();
    verify(histories, never()).save(any());
  }

  @Test
  void decisionRecordsReasonHistoryAndStatusOnlyAudit() {
    actor();
    lock();
    ReflectionTestUtils.setField(applicant, "status", ApplicantStatus.INTERVIEWED);
    var response =
        service.decide(
            STORE_ID,
            "a",
            new ApplicantTransitionRequest(0L, ApplicantStatus.HIRED, "  面接で合意  "),
            "actor");
    assertThat(response.status()).isEqualTo(ApplicantStatus.HIRED);
    assertThat(applicant.getModifiedBy()).isEqualTo(9L);
    var captor = ArgumentCaptor.forClass(ApplicantStatusHistory.class);
    verify(histories).save(captor.capture());
    var history = captor.getValue();
    assertThat(history.getPreviousStatus()).isEqualTo(ApplicantStatus.INTERVIEWED);
    assertThat(history.getNewStatus()).isEqualTo(ApplicantStatus.HIRED);
    assertThat(history.getReason()).isEqualTo("面接で合意");
    assertThat(history.getActorId()).isEqualTo(9L);
    assertThat(history.getStoreId()).isEqualTo(STORE_ID);
    verify(audit)
        .recordCurrent(
            STORE_ID,
            "APPLICANT_DECIDED",
            "APPLICANT",
            "a",
            Map.of("status", "INTERVIEWED", "version", "0"),
            Map.of("status", "HIRED", "version", "0"));
  }

  @Test
  void invalidOrStaleDecisionLeavesNoTrace() {
    lock();
    assertThatThrownBy(
            () ->
                service.decide(
                    STORE_ID,
                    "a",
                    new ApplicantTransitionRequest(0L, ApplicantStatus.HIRED, "面接なし"),
                    "actor"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                service.decide(
                    STORE_ID,
                    "a",
                    new ApplicantTransitionRequest(3L, ApplicantStatus.REJECTED, "旧版"),
                    "actor"))
        .isInstanceOf(ConflictException.class);
    for (var status : List.of(ApplicantStatus.HIRED, ApplicantStatus.REJECTED))
      assertThatThrownBy(
              () ->
                  service.transition(
                      STORE_ID, "a", new ApplicantTransitionRequest(0L, status, "通常遷移"), "actor"))
          .isInstanceOf(ServiceException.class);
    assertThat(applicant.getStatus()).isEqualTo(ApplicantStatus.RECEIVED);
    assertThat(applicant.getEditSequence()).isZero();
    verify(histories, never()).save(any());
    verify(applicants, never()).flush();
    verifyNoInteractions(audit);
  }

  private static ApplicantEnrollmentRequest enrollment(Long version) {
    return new ApplicantEnrollmentRequest(
        version,
        "源氏名",
        "SUSPENDED",
        LocalDate.of(2026, 11, 1),
        null,
        "紹介",
        20,
        null,
        null,
        null,
        null,
        3);
  }

  @Test
  void enrollmentCreatesCastFromOperatorInputAndAuditsTheLink() {
    actor();
    lock();
    ReflectionTestUtils.setField(applicant, "status", ApplicantStatus.HIRED);
    var command = ArgumentCaptor.forClass(CastCreateCommand.class);
    var enrollment = CastEnrollment.builder().build();
    enrollment.setId("e1");
    when(casts.create(eq(STORE_ID), command.capture(), eq("actor")))
        .thenReturn(new CastCreation(enrollment, new CastProfile(), true));
    var response = service.enroll(STORE_ID, "a", enrollment(0L), "actor");
    assertThat(response.enrollmentId()).isEqualTo("e1");
    assertThat(response.status()).isEqualTo(ApplicantStatus.HIRED);
    assertThat(applicant.getModifiedBy()).isEqualTo(9L);
    assertThat(command.getValue())
        .isEqualTo(
            new CastCreateCommand(
                "源氏名",
                CastEnrollmentStatus.SUSPENDED,
                LocalDate.of(2026, 11, 1),
                null,
                "紹介",
                20,
                null,
                null,
                null,
                null,
                3));
    verify(histories, never()).save(any());
    verify(audit)
        .recordCurrent(
            STORE_ID,
            "APPLICANT_ENROLLED",
            "APPLICANT",
            "a",
            Map.of("enrollment_id", "", "version", "0"),
            Map.of("enrollment_id", "e1", "version", "0"));
  }

  @Test
  void enrollmentRefusesBeforeCreatingAnything() {
    lock();
    assertThatThrownBy(() -> service.enroll(STORE_ID, "a", enrollment(0L), "actor"))
        .isInstanceOf(ServiceException.class);
    ReflectionTestUtils.setField(applicant, "status", ApplicantStatus.HIRED);
    assertThatThrownBy(() -> service.enroll(STORE_ID, "a", enrollment(3L), "actor"))
        .isInstanceOf(ConflictException.class);
    applicant.recordEnrollment("existing");
    assertThatThrownBy(() -> service.enroll(STORE_ID, "a", enrollment(0L), "actor"))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(casts, audit);
    verify(applicants, never()).flush();
  }

  @Test
  void scopeInvisibleReadsAndMutationsFailClosed() {
    assertThatThrownBy(() -> service.get(STORE_ID, "outside"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.history(STORE_ID, "outside", null, 20))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.update(
                    STORE_ID, "outside", new ApplicantUpdateRequest(0L, intake), "actor"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.enroll(STORE_ID, "outside", enrollment(0L), "actor"))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(casts);
    verify(histories, never())
        .findByStoreIdAndApplicantIdOrderByCreatedAtDescIdDesc(any(), any(), any());
  }

  @Test
  void applicantIsReadOnlyWithinTheGivenStore() {
    // 別店舗の id での呼出を厳格スタブの引数不一致ではなく未検出として観測するため lenient にする。
    lenient().when(applicants.findByIdAndStoreId("a", STORE_ID)).thenReturn(Optional.of(applicant));
    assertThat(service.get(STORE_ID, "a").id()).isEqualTo("a");
    assertThatThrownBy(() -> service.get(STORE_ID + 1, "a")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void missingActorCannotCreateRecords() {
    assertThatThrownBy(() -> service.create(STORE_ID, intake, "missing"))
        .isInstanceOf(StaleSessionException.class);
    verify(applicants, never()).saveAndFlush(any());
  }

  @Test
  void auditFailurePropagatesToTheBusinessTransaction() {
    actor();
    lock();
    doThrow(new IllegalStateException("監査を保存できません"))
        .when(audit)
        .recordCurrent(eq(STORE_ID), any(), any(), any(), any(), any());
    assertThatThrownBy(
            () -> service.update(STORE_ID, "a", new ApplicantUpdateRequest(0L, intake), "actor"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("監査を保存できません");
  }

  @Test
  void historyCursorUsesSameTupleAndCapsSize() {
    when(applicants.findByIdAndStoreId("a", STORE_ID)).thenReturn(Optional.of(applicant));
    var row =
        ApplicantStatusHistory.builder()
            .applicantId("a")
            .actorId(9L)
            .newStatus(ApplicantStatus.RECEIVED)
            .reason("受付")
            .build();
    row.setId("h");
    row.setCreatedAt(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
    when(histories.findByStoreIdAndApplicantIdOrderByCreatedAtDescIdDesc(
            STORE_ID, "a", Limit.of(2)))
        .thenReturn(List.of(row, row));
    var page = service.history(STORE_ID, "a", null, 1);
    assertThat(page.content()).hasSize(1);
    when(histories.findAfter(STORE_ID, "a", row.getCreatedAt(), "h", Limit.of(101)))
        .thenReturn(List.of());
    assertThat(service.history(STORE_ID, "a", page.nextCursor(), 1000).content()).isEmpty();
  }
}
