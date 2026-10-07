package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.recruitment.api.dto.ApplicantIntakeRequest;
import com.kizuna.recruitment.api.dto.ApplicantInterviewRequest;
import com.kizuna.recruitment.api.dto.ApplicantMapper;
import com.kizuna.recruitment.api.dto.ApplicantTransitionRequest;
import com.kizuna.recruitment.api.dto.ApplicantUpdateRequest;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import com.kizuna.recruitment.domain.ApplicantStatusHistoryRepository;
import com.kizuna.recruitment.domain.ReceptionChannel;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
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
  @Mock ApplicantRepository applicants;
  @Mock ApplicantStatusHistoryRepository histories;
  @Mock PlatformUserRepository users;
  @Mock BusinessAudit audit;
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
            applicants, histories, users, Mappers.getMapper(ApplicantMapper.class), audit);
    applicant = Applicant.receive(intake.toIntake());
    applicant.setId("a");
    applicant.setStoreId(1L);
    ReflectionTestUtils.setField(applicant, "version", 0L);
  }

  void actor() {
    PlatformUser user = new PlatformUser();
    user.setId(9L);
    when(users.findByEmail("actor")).thenReturn(Optional.of(user));
  }

  void lock() {
    when(applicants.findScopedForUpdate("a")).thenReturn(Optional.of(applicant));
  }

  @Test
  void createsPrivateApplicantAndInitialHistory() {
    actor();
    when(applicants.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              Applicant value = call.getArgument(0);
              value.setId("a");
              value.setStoreId(1L);
              ReflectionTestUtils.setField(value, "version", 0L);
              return value;
            });
    var response = service.create(intake, "actor");
    assertThat(response.status()).isEqualTo(ApplicantStatus.RECEIVED);
    assertThat(response.address()).isEqualTo("住所");
    assertThat(response.modifiedBy()).isEqualTo(9L);
    verify(audit)
        .recordCurrent(
            1L,
            "APPLICANT_RECEIVED",
            "APPLICANT",
            "a",
            Map.of(),
            Map.of("status", "RECEIVED", "version", "0"));
    var captor = ArgumentCaptor.forClass(ApplicantStatusHistory.class);
    verify(histories).save(captor.capture());
    assertThat(captor.getValue().getPreviousStatus()).isNull();
    assertThat(captor.getValue().getActorId()).isEqualTo(9L);
  }

  @Test
  void editsEvenIdenticalIntakeAndInterviewUnderVersionCheck() {
    actor();
    lock();
    service.update("a", new ApplicantUpdateRequest(0L, intake), "actor");
    service.interview(
        "a",
        new ApplicantInterviewRequest(0L, OffsetDateTime.now(), "担当", "私用メモ", Map.of("確認", true)),
        "actor");
    assertThat(applicant.getEditSequence()).isEqualTo(2);
    assertThat(applicant.getInterview().notes()).isEqualTo("私用メモ");
    var safe = Map.of("status", "RECEIVED", "version", "0");
    verify(audit).recordCurrent(1L, "APPLICANT_INTAKE_UPDATED", "APPLICANT", "a", safe, safe);
    verify(audit).recordCurrent(1L, "APPLICANT_INTERVIEW_UPDATED", "APPLICANT", "a", safe, safe);
  }

  @Test
  void transitionRecordsReasonActorAndPreviousState() {
    actor();
    lock();
    service.transition(
        "a", new ApplicantTransitionRequest(0L, ApplicantStatus.SCREENING, "  選考開始  "), "actor");
    var captor = ArgumentCaptor.forClass(ApplicantStatusHistory.class);
    verify(histories).save(captor.capture());
    var history = captor.getValue();
    assertThat(history.getPreviousStatus()).isEqualTo(ApplicantStatus.RECEIVED);
    assertThat(history.getNewStatus()).isEqualTo(ApplicantStatus.SCREENING);
    assertThat(history.getReason()).isEqualTo("選考開始");
    assertThat(history.getActorId()).isEqualTo(9L);
    verify(audit)
        .recordCurrent(
            1L,
            "APPLICANT_STATUS_CHANGED",
            "APPLICANT",
            "a",
            Map.of("status", "RECEIVED", "version", "0"),
            Map.of("status", "SCREENING", "version", "0"));
  }

  @Test
  void rejectsStaleBeforeAnyMutation() {
    lock();
    assertThatThrownBy(() -> service.update("a", new ApplicantUpdateRequest(3L, intake), "actor"))
        .isInstanceOf(ConflictException.class);
    assertThat(applicant.getEditSequence()).isZero();
    verifyNoInteractions(audit);
    verify(applicants, never()).flush();
    verify(histories, never()).save(any());
  }

  @Test
  void finalDecisionsRemainUnconfiguredAndNormalTransitionCannotBypass() {
    lock();
    for (var status : List.of(ApplicantStatus.HIRED, ApplicantStatus.REJECTED)) {
      var request = new ApplicantTransitionRequest(0L, status, "判断理由");
      assertThatThrownBy(() -> service.decide("a", request))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("未設定");
      assertThatThrownBy(() -> service.transition("a", request, "actor"))
          .isInstanceOf(ServiceException.class);
    }
    assertThatThrownBy(
            () ->
                service.decide(
                    "a", new ApplicantTransitionRequest(0L, ApplicantStatus.SCREENING, "確認")))
        .isInstanceOf(ServiceException.class);
    assertThat(applicant.getStatus()).isEqualTo(ApplicantStatus.RECEIVED);
    verify(histories, never()).save(any());
  }

  @Test
  void scopeInvisibleReadsAndMutationsFailClosed() {
    assertThatThrownBy(() -> service.get("outside")).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.history("outside", null, 20))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> service.update("outside", new ApplicantUpdateRequest(0L, intake), "actor"))
        .isInstanceOf(NotFoundException.class);
    verify(histories, never()).findByApplicantIdOrderByCreatedAtDescIdDesc(any(), any());
  }

  @Test
  void missingActorCannotCreateRecords() {
    assertThatThrownBy(() -> service.create(intake, "missing"))
        .isInstanceOf(StaleSessionException.class);
    verify(applicants, never()).saveAndFlush(any());
  }

  @Test
  void auditFailurePropagatesToTheBusinessTransaction() {
    actor();
    lock();
    doThrow(new IllegalStateException("監査を保存できません"))
        .when(audit)
        .recordCurrent(eq(1L), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> service.update("a", new ApplicantUpdateRequest(0L, intake), "actor"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("監査を保存できません");
  }

  @Test
  void historyCursorUsesSameTupleAndCapsSize() {
    when(applicants.findById("a")).thenReturn(Optional.of(applicant));
    var row =
        ApplicantStatusHistory.builder()
            .applicantId("a")
            .actorId(9L)
            .newStatus(ApplicantStatus.RECEIVED)
            .reason("受付")
            .build();
    row.setId("h");
    row.setCreatedAt(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
    when(histories.findByApplicantIdOrderByCreatedAtDescIdDesc("a", Limit.of(2)))
        .thenReturn(List.of(row, row));
    var page = service.history("a", null, 1);
    assertThat(page.content()).hasSize(1);
    when(histories.findAfter("a", row.getCreatedAt(), "h", Limit.of(101))).thenReturn(List.of());
    assertThat(service.history("a", page.nextCursor(), 1000).content()).isEmpty();
  }
}
