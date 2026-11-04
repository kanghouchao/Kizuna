package com.kizuna.pointsexpiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.member.application.MemberDisplayNames;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.notificationdelivery.intake.MemberNotices;
import com.kizuna.point.application.PointExpiryLedger;
import com.kizuna.point.domain.ExpiringCreditView;
import com.kizuna.point.domain.PointAllocationRepository;
import com.kizuna.point.domain.PointConsumption;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.settings.application.PointExpiryNoticeSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.task.execution.TaskContext;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

class PointExpiryNoticeTaskTest {
  private final PointEntryRepository entries = mock(PointEntryRepository.class);
  private final PointAllocationRepository allocations = mock(PointAllocationRepository.class);
  private final MemberDisplayNames names = mock(MemberDisplayNames.class);
  private final MemberNotices notices = mock(MemberNotices.class);
  private final SystemConfigService configs = mock(SystemConfigService.class);
  private final ServiceExecutionIdentityService identities =
      mock(ServiceExecutionIdentityService.class);
  private final AppProperties properties = new AppProperties();
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneId.of("Asia/Tokyo"));
  private final LocalDate today = LocalDate.now(clock);
  private final LocalDate target = today.plusDays(7);
  private final TaskContext context = new TaskContext(4L, 8L, today, today);
  private final AuditActor actor = new AuditActor(8L, "SERVICE", "案内処理");
  private final PointExpiryNoticeTask task =
      new PointExpiryNoticeTask(
          new PointExpiryLedger(entries, allocations, mock(MemberLookupService.class)),
          names,
          notices,
          configs,
          identities,
          properties,
          clock);

  @BeforeEach
  void prepare() {
    when(identities.requireService(eq(8L), any())).thenReturn(actor);
    when(configs.pointExpiryNoticeSettings()).thenReturn(new PointExpiryNoticeSettings(7));
    when(names.of(anyList())).thenReturn(Map.of(7L, "山田", 9L, "佐藤"));
  }

  @Test
  void issuesOneNoticePerMemberForTheRemainderExpiringOnTheTargetDay() {
    when(entries.findUnconsumedCreditsExpiringOn(eq(target), any()))
        .thenReturn(List.of(credit(1, 7, 1000), credit(2, 7, 500), credit(3, 9, 80)));
    when(allocations.findConsumedBySourceEntryIds(anyList()))
        .thenReturn(List.of(consumption(1, 300)));
    when(notices.queueBusinessEmail(eq(actor), any())).thenReturn(true, false);
    assertThat(task.execute(context)).isEqualTo(1);
    var issued = ArgumentCaptor.forClass(MemberNotices.Notice.class);
    verify(notices, times(2)).queueBusinessEmail(eq(actor), issued.capture());
    var first = issued.getAllValues().getFirst();
    assertThat(first.memberId()).isEqualTo(7L);
    assertThat(first.dedupeKey()).isEqualTo("point-expiry-notice:7:2026-10-15");
    assertThat(first.subject()).isEqualTo("ポイントの有効期限のお知らせ");
    assertThat(first.body()).contains("山田 様", "1,200 ポイント", "2026年10月15日");
    assertThat(issued.getAllValues().get(1).body()).contains("佐藤 様", "80 ポイント");
  }

  @Test
  void noticeDaysMustBeConfiguredBeforeReadingTheLedger() {
    when(configs.pointExpiryNoticeSettings()).thenReturn(new PointExpiryNoticeSettings(null));
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(ServiceException.class);
    verifyNoInteractions(entries, notices);
  }

  @Test
  void invalidOrFutureRangeIsRejected() {
    for (var invalid :
        List.of(
            new TaskContext(4L, 8L, today.plusDays(1), today.plusDays(1)),
            new TaskContext(4L, 8L, today.minusDays(1), today),
            new TaskContext(4L, 8L, null, null))) {
      assertThatThrownBy(() -> task.execute(invalid)).isInstanceOf(ServiceException.class);
    }
    verifyNoInteractions(entries, notices);
  }

  @Test
  void resourceLimitFailsBeforeIssuing() {
    properties.getPointsExpiryNotice().setMaxLots(1);
    when(entries.findUnconsumedCreditsExpiringOn(eq(target), any()))
        .thenReturn(List.of(credit(1, 7, 10), credit(2, 9, 10)));
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(ServiceException.class);
    verifyNoInteractions(notices);
    properties.getPointsExpiryNotice().setMaxLots(0);
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(ServiceException.class);
  }

  @Test
  void validatesIdentityAgainBeforeEachIssue() {
    when(entries.findUnconsumedCreditsExpiringOn(eq(target), any()))
        .thenReturn(List.of(credit(1, 7, 10)));
    when(identities.requireService(8L, PermissionCode.POINT_EXPIRE))
        .thenReturn(actor)
        .thenThrow(new AccessDeniedException("権限なし"));
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(notices);
  }

  @Test
  void ledgerMemberWithoutAMemberRowFailsTheRun() {
    when(entries.findUnconsumedCreditsExpiringOn(eq(target), any()))
        .thenReturn(List.of(credit(1, 11, 10)));
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(notices);
  }

  @Test
  void registryMetadataAndEmptyRunAreAccurate() {
    assertThat(task.name()).isEqualTo("POINT_EXPIRY_NOTICE");
    assertThat(task.permission()).isEqualTo(PermissionCode.POINT_EXPIRE);
    assertThat(task.manualPermission()).isEqualTo(Optional.of(PermissionCode.POINT_EXPIRE));
    assertThat(task.execute(context)).isZero();
    verifyNoInteractions(notices);
  }

  private ExpiringCreditView credit(long id, long member, int amount) {
    return new ExpiringCreditView() {
      public Long getId() {
        return id;
      }

      public Long getMemberId() {
        return member;
      }

      public int getAmount() {
        return amount;
      }

      public LocalDate getExpiresOn() {
        return target;
      }
    };
  }

  private PointConsumption consumption(long id, long amount) {
    return new PointConsumption() {
      public Long getSourceEntryId() {
        return id;
      }

      public Long getConsumed() {
        return amount;
      }
    };
  }
}
