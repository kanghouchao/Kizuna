package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.settings.application.ApplicantRetentionSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.task.execution.TaskContext;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ApplicantAnonymizationTaskTest {
  private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
  private final ApplicantRepository applicants = mock(ApplicantRepository.class);
  private final SystemConfigService systemConfigs = mock(SystemConfigService.class);
  private final ServiceExecutionIdentityService identities =
      mock(ServiceExecutionIdentityService.class);
  private final AppProperties properties = new AppProperties();
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-29T15:30:00Z"), TOKYO);
  private final LocalDate today = LocalDate.now(clock);
  private final TaskContext context = new TaskContext(4L, 8L, today, today);
  private final ApplicantAnonymizationTask task =
      new ApplicantAnonymizationTask(
          applicants,
          mock(ApplicantAttachmentStore.class),
          systemConfigs,
          identities,
          mock(AuditWriter.class),
          properties,
          clock);

  @BeforeEach
  void authorize() {
    var actor = new AuditActor(8L, "SERVICE", "匿名化処理");
    when(identities.requireService(eq(8L), any())).thenReturn(actor);
    when(identities.requireServiceScope(eq(8L), any()))
        .thenReturn(
            new ServiceExecutionIdentityService.ServiceScope(actor, new StoreScope(Set.of(1L))));
  }

  @Test
  void declaresTheDedicatedPermissionForServiceAndManualRuns() {
    assertThat(task.name()).isEqualTo("APPLICANT_ANONYMIZATION");
    assertThat(task.permission()).isEqualTo(PermissionCode.APPLICANT_ANONYMIZE);
    assertThat(task.manualPermission()).contains(PermissionCode.APPLICANT_ANONYMIZE);
  }

  @Test
  void unsetPeriodsAnonymizeNothing() {
    when(systemConfigs.applicantRetentionSettings())
        .thenReturn(new ApplicantRetentionSettings(null, null, null));
    assertThat(task.execute(context)).isZero();
    verify(applicants, never()).findAnonymizationTargetsIn(any(), any(), any(), any());
  }

  @Test
  void onlyStatesWithAPeriodAreQueriedAndTheRunLimitIsShared() {
    properties.getApplicantAnonymization().setMaxApplicants(5);
    when(systemConfigs.applicantRetentionSettings())
        .thenReturn(new ApplicantRetentionSettings(6, null, 0));
    when(applicants.findAnonymizationTargetsIn(any(), any(), any(), any())).thenReturn(List.of());
    task.execute(context);
    verify(applicants)
        .findAnonymizationTargetsIn(
            eq(ApplicantStatus.REJECTED),
            eq(OffsetDateTime.parse("2026-05-01T00:00+09:00")),
            eq(Set.of(1L)),
            any());
    verify(applicants)
        .findAnonymizationTargetsIn(
            eq(ApplicantStatus.HIRED),
            eq(OffsetDateTime.parse("2026-10-31T00:00+09:00")),
            eq(Set.of(1L)),
            any());
    verify(applicants, never())
        .findAnonymizationTargetsIn(eq(ApplicantStatus.WITHDRAWN), any(), any(), any());
  }

  @Test
  void serviceWithAnEmptyStoreSetQueriesNoStore() {
    when(identities.requireServiceScope(eq(8L), any()))
        .thenReturn(
            new ServiceExecutionIdentityService.ServiceScope(
                new AuditActor(8L, "SERVICE", "匿名化処理"), new StoreScope(Set.of())));
    when(systemConfigs.applicantRetentionSettings())
        .thenReturn(new ApplicantRetentionSettings(6, 6, 0));
    assertThat(task.execute(context)).isZero();
    verify(applicants, never()).findAnonymizationTargetsIn(any(), any(), any(), any());
  }

  @Test
  void dueDateFollowsMonthEndClampingOfTransitionPlusPeriod() {
    // 1/31 + 1 か月 = 2/28 なので、2/28 時点では 1/31 までの遷移が満了している。
    assertThat(ApplicantAnonymizationTask.transitionedBefore(LocalDate.of(2026, 2, 28), 1, TOKYO))
        .isEqualTo(OffsetDateTime.parse("2026-02-01T00:00+09:00"));
    // 2/28 + 1 か月 = 3/28 なので、3/27 時点では 2/27 までの遷移だけが満了している。
    assertThat(ApplicantAnonymizationTask.transitionedBefore(LocalDate.of(2026, 3, 27), 1, TOKYO))
        .isEqualTo(OffsetDateTime.parse("2026-02-28T00:00+09:00"));
    assertThat(ApplicantAnonymizationTask.transitionedBefore(LocalDate.of(2026, 3, 31), 1, TOKYO))
        .isEqualTo(OffsetDateTime.parse("2026-03-01T00:00+09:00"));
    // 0 か月は当日中の遷移までを満了とする。
    assertThat(ApplicantAnonymizationTask.transitionedBefore(today, 0, TOKYO))
        .isEqualTo(OffsetDateTime.parse("2026-10-31T00:00+09:00"));
  }

  @Test
  void invalidOrFutureRangeIsRejected() {
    for (var invalid :
        List.of(
            new TaskContext(4L, 8L, today.plusDays(1), today.plusDays(1)),
            new TaskContext(4L, 8L, today.minusDays(1), today))) {
      assertThatThrownBy(() -> task.execute(invalid)).isInstanceOf(ServiceException.class);
    }
    verifyNoInteractions(applicants, systemConfigs);
  }

  @Test
  void serviceWithoutTheTaskPermissionTouchesNothing() {
    when(identities.requireServiceScope(8L, PermissionCode.APPLICANT_ANONYMIZE))
        .thenThrow(new AccessDeniedException("denied"));
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(applicants, systemConfigs);
  }
}
