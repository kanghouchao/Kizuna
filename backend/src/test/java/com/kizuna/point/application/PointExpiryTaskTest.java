package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.point.domain.PointAllocationRepository;
import com.kizuna.point.domain.PointConsumption;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.pointsexpiry.application.PointExpiryTask;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

class PointExpiryTaskTest {
  private final PointEntryRepository entries = mock(PointEntryRepository.class);
  private final PointAllocationRepository allocations = mock(PointAllocationRepository.class);
  private final ServiceExecutionIdentityService identities =
      mock(ServiceExecutionIdentityService.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final AppProperties properties = new AppProperties();
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneId.of("Asia/Tokyo"));
  private final LocalDate today = LocalDate.now(clock);
  private final TaskContext context = new TaskContext(4L, 8L, null, today, today);
  private final PointExpiryTask task =
      new PointExpiryTask(
          new PointExpiryLedger(entries, allocations), identities, audit, properties, clock);

  @BeforeEach
  void authorize() {
    when(identities.requireService(eq(8L), any(), isNull()))
        .thenReturn(new AuditActor(8L, "SERVICE", "失効処理"));
    when(entries.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              PointEntry entry = call.getArgument(0);
              entry.setId(77L);
              return entry;
            });
  }

  @Test
  void recordsOnlyExpiredRemaindersAndLinksTheRealExecutionWithoutActor() {
    var due = credit(1, 100, today.minusDays(1));
    var due2 = credit(2, 50, today.minusDays(20));
    when(entries.findExpiryCandidates(eq(today), any())).thenReturn(List.of(1L, 2L));
    when(entries.lockExpiryCandidates(anyList())).thenReturn(List.of(due, due2));
    when(allocations.findConsumedBySourceEntryIds(anyList()))
        .thenReturn(List.of(consumption(1, 30)));
    assertThat(task.execute(context)).isEqualTo(1);
    var captured = ArgumentCaptor.forClass(PointEntry.class);
    verify(entries).saveAndFlush(captured.capture());
    var entry = captured.getValue();
    assertThat(entry.getAmount()).isEqualTo(-120);
    assertThat(entry.getActorUserId()).isNull();
    assertThat(entry.getOriginatingStoreId()).isNull();
    assertThat(entry.getExpiryKey()).isEqualTo("expiry:4:7");
    assertThat(entry.getAllocations()).hasSize(2);
    var change = ArgumentCaptor.forClass(AuditChange.class);
    verify(audit).append(change.capture());
    assertThat(change.getValue().sourceId()).isEqualTo("4");
    assertThat(change.getValue().targetId()).isEqualTo("77");
    var order = inOrder(entries, allocations);
    order.verify(entries).lockExpiryCandidates(anyList());
    order.verify(allocations).findConsumedBySourceEntryIds(anyList());
  }

  @Test
  void neverExpiresTodayOrUnlimitedLotsAndSkipsConcurrentConsumption() {
    when(entries.findExpiryCandidates(eq(today), any())).thenReturn(List.of(1L, 2L, 3L));
    when(entries.lockExpiryCandidates(anyList()))
        .thenReturn(
            List.of(
                credit(1, 100, today), credit(2, 100, null), credit(3, 100, today.minusDays(1))));
    when(allocations.findConsumedBySourceEntryIds(anyList()))
        .thenReturn(List.of(consumption(3, 100)));
    assertThat(task.execute(context)).isZero();
    verify(entries, never()).saveAndFlush(any());
    verifyNoInteractions(audit);
  }

  @Test
  void resourceLimitFailsBeforeLocksOrWrites() {
    properties.getPointsExpiry().setMaxLots(1);
    when(entries.findExpiryCandidates(eq(today), any())).thenReturn(List.of(1L, 2L));
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(ServiceException.class);
    verify(entries, never()).lockExpiryCandidates(anyList());
    verifyNoInteractions(audit);
  }

  @Test
  void invalidRangeFutureOrStoreIsRejected() {
    for (var invalid :
        List.of(
            new TaskContext(4L, 8L, null, today.plusDays(1), today.plusDays(1)),
            new TaskContext(4L, 8L, null, today.minusDays(1), today),
            new TaskContext(4L, 8L, 1L, today, today))) {
      assertThatThrownBy(() -> task.execute(invalid)).isInstanceOf(ServiceException.class);
    }
    verifyNoInteractions(entries);
  }

  @Test
  void validatesIdentityAgainBeforeAuditAndCommit() {
    when(entries.findExpiryCandidates(eq(today), any())).thenReturn(List.of(1L));
    when(entries.lockExpiryCandidates(anyList()))
        .thenReturn(List.of(credit(1, 100, today.minusDays(1))));
    when(identities.requireService(8L, PermissionCode.POINT_EXPIRE, null))
        .thenReturn(new AuditActor(8L, "SERVICE", "失効処理"))
        .thenThrow(new AccessDeniedException("権限なし"));
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void combinedAmountCannotOverflowIntoAnotherDebit() {
    when(entries.findExpiryCandidates(eq(today), any())).thenReturn(List.of(1L, 2L));
    when(entries.lockExpiryCandidates(anyList()))
        .thenReturn(
            List.of(
                credit(1, Integer.MAX_VALUE, today.minusDays(1)),
                credit(2, 1, today.minusDays(1))));
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(ArithmeticException.class);
    verify(entries, never()).saveAndFlush(any());
  }

  @Test
  void registryMetadataAndEmptyRunAreAccurate() {
    assertThat(task.name()).isEqualTo("POINT_EXPIRY");
    assertThat(task.permission()).isEqualTo(PermissionCode.POINT_EXPIRE);
    assertThat(task.platformWide()).isTrue();
    assertThat(task.execute(context)).isZero();
    properties.getPointsExpiry().setMaxLots(0);
    assertThatThrownBy(() -> task.execute(context)).isInstanceOf(ServiceException.class);
  }

  private PointEntry credit(long id, int amount, LocalDate date) {
    var entry = PointEntry.manualAdjust(7L, null, amount, "検証", date, List.of(), 8L, "grant:" + id);
    entry.setId(id);
    return entry;
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
