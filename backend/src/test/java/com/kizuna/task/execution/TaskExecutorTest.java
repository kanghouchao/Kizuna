package com.kizuna.task.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskRegistry;
import com.kizuna.task.domain.ExecutionAttempt;
import com.kizuna.task.domain.ExecutionAttemptRepository;
import com.kizuna.task.domain.ExecutionRequest;
import com.kizuna.task.domain.ExecutionRequestRepository;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TaskExecutorTest {
  private final Map<Long, ExecutionRequest> requests = new HashMap<>();
  private final Map<Long, ExecutionAttempt> attempts = new HashMap<>();
  private final ExecutionRequestRepository requestRepo = mock(ExecutionRequestRepository.class);
  private final ExecutionAttemptRepository attemptRepo = mock(ExecutionAttemptRepository.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final ServiceExecutionIdentityService identities =
      mock(ServiceExecutionIdentityService.class);
  private final TaskHandler handler = mock(TaskHandler.class);
  private final StoreRepository stores = mock(StoreRepository.class);
  private final StoreContext scope = new StoreContext();
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T01:00:00Z"), ZoneOffset.UTC);
  private final AuditActor service = new AuditActor(10L, "SERVICE", "実行主体");
  private final AuditActor operator = new AuditActor(20L, "STAFF", "操作担当");
  private TaskLifecycle lifecycle;
  private TaskExecutor executor;

  @BeforeEach
  void setup() {
    when(requestRepo.findByTaskNameAndLogicalKey(anyString(), anyString()))
        .thenAnswer(
            call ->
                requests.values().stream()
                    .filter(
                        r ->
                            r.getTaskName().equals(call.getArgument(0))
                                && r.getLogicalKey().equals(call.getArgument(1)))
                    .findFirst());
    when(requestRepo.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ExecutionRequest r = call.getArgument(0);
              r.setId((long) requests.size() + 1);
              requests.put(r.getId(), r);
              return r;
            });
    when(attemptRepo.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ExecutionAttempt a = call.getArgument(0);
              a.setId((long) attempts.size() + 1);
              attempts.put(a.getId(), a);
              return a;
            });
    when(requestRepo.findById(anyLong()))
        .thenAnswer(call -> Optional.ofNullable(requests.get(call.getArgument(0))));
    when(requestRepo.lockById(anyLong()))
        .thenAnswer(call -> Optional.ofNullable(requests.get(call.getArgument(0))));
    when(requestRepo.lockIdleById(anyLong()))
        .thenAnswer(call -> Optional.ofNullable(requests.get(call.getArgument(0))));
    when(attemptRepo.findById(anyLong()))
        .thenAnswer(call -> Optional.ofNullable(attempts.get(call.getArgument(0))));
    when(attemptRepo.findRequestId(anyLong()))
        .thenAnswer(
            call ->
                Optional.ofNullable(attempts.get(call.getArgument(0)))
                    .map(ExecutionAttempt::getRequestId));
    when(attemptRepo.findFirstByRequestIdOrderByAttemptNumberDesc(anyLong()))
        .thenAnswer(
            call ->
                attempts.values().stream()
                    .filter(a -> a.getRequestId().equals(call.getArgument(0)))
                    .max(Comparator.comparingInt(ExecutionAttempt::getAttemptNumber)));
    when(identities.requireService(anyLong(), any(), nullable(Long.class))).thenReturn(service);
    when(handler.name()).thenReturn("CHECK");
    when(handler.platformWide()).thenReturn(true);
    when(handler.permission()).thenReturn(PermissionCode.TASK_EXECUTE);
    var transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    lifecycle = new TaskLifecycle(requestRepo, attemptRepo, audit, clock, stores);
    executor =
        new TaskExecutor(
            new TaskRegistry(List.of(handler)),
            identities,
            lifecycle,
            scope,
            clock,
            transactions,
            new AppProperties());
  }

  private TaskCommand command(String key) {
    return new TaskCommand(
        "CHECK", key, 10L, null, LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 7));
  }

  @Test
  void sameRequestReplaysWhileDifferentInputIsRejected() {
    when(handler.execute(any())).thenReturn(4L);
    var first = executor.execute(command("daily"), operator);
    assertThat(first.created()).isTrue();
    assertThat(first.execution().status()).isEqualTo("SUCCEEDED");
    assertThat(first.execution().processedCount()).isEqualTo(4);
    assertThat(first.execution().initiatedBy()).isEqualTo(operator.id());
    var replay = executor.execute(command("daily"), operator);
    assertThat(replay.created()).isFalse();
    assertThat(replay.execution().id()).isEqualTo(first.execution().id());
    assertThat(lifecycle.replay(command("daily"))).isEqualTo(replay.execution());
    verify(handler).execute(any());
    assertThatThrownBy(
            () ->
                executor.execute(
                    new TaskCommand(
                        "CHECK",
                        "daily",
                        11L,
                        null,
                        LocalDate.of(2026, 10, 7),
                        LocalDate.of(2026, 10, 7)),
                    operator))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> executor.retry(first.execution().id(), "二重", operator))
        .isInstanceOf(ConflictException.class);
    var events = org.mockito.ArgumentCaptor.forClass(AuditChange.class);
    verify(audit, times(2)).append(events.capture());
    assertThat(events.getAllValues())
        .extracting(AuditChange::result)
        .containsExactly("RUNNING", "SUCCEEDED");
    assertThat(events.getAllValues().getFirst().actor()).isEqualTo(operator);
  }

  @Test
  void failureKeepsReasonCodeAndRetryUsesCurrentNameWithoutRewritingHistory() {
    when(handler.execute(any())).thenThrow(new IllegalStateException("private payload"));
    var failed = executor.executeScheduled(command("failure")).execution();
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(failed.failureCode()).isEqualTo("EXECUTION_FAILED");
    assertThat(failed.toString()).doesNotContain("private payload");
    assertThatThrownBy(() -> executor.retry(failed.id(), " ", operator))
        .isInstanceOf(ServiceException.class);
    doReturn(2L).when(handler).execute(any());
    when(identities.requireService(anyLong(), any(), nullable(Long.class)))
        .thenReturn(new AuditActor(10L, "SERVICE", "新しい実行主体名"));
    var retried = executor.retry(failed.id(), "復旧を確認", operator);
    assertThat(retried.status()).isEqualTo("SUCCEEDED");
    assertThat(retried.retryOf()).isEqualTo(failed.id());
    assertThat(retried.reason()).isEqualTo("復旧を確認");
    assertThat(retried.serviceName()).isEqualTo("新しい実行主体名");
    assertThat(lifecycle.get(failed.id()).serviceName()).isEqualTo("実行主体");
    assertThat(lifecycle.get(failed.id()).status()).isEqualTo("FAILED");
    assertThatThrownBy(() -> executor.retry(failed.id(), "古い試行", operator))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void stoppedServiceIsRejectedBeforeCreationAndAgainBeforeWork() {
    when(identities.requireService(anyLong(), any(), nullable(Long.class)))
        .thenThrow(new AccessDeniedException("停止"));
    assertThatThrownBy(() -> executor.executeScheduled(command("denied")))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(attempts).isEmpty();
    doReturn(service)
        .doThrow(new AccessDeniedException("停止"))
        .when(identities)
        .requireService(anyLong(), any(), nullable(Long.class));
    var denied = executor.executeScheduled(command("revoked")).execution();
    assertThat(denied.failureCode()).isEqualTo("AUTHORIZATION_DENIED");
    verify(handler, never()).execute(any());
  }

  @Test
  void interruptedStartIsRecoverableButFinishedRecordsRemainImmutable() {
    var start = lifecycle.begin(command("abandoned"), service, null, "SCHEDULED");
    assertThatThrownBy(() -> lifecycle.interrupt(start.id(), "停止確認", operator, 300))
        .isInstanceOf(ConflictException.class);
    assertThat(lifecycle.interrupt(start.id(), "停止確認", operator, 0).status())
        .isEqualTo("INTERRUPTED");
    assertThat(lifecycle.fail(start.id(), "EXECUTION_FAILED").status()).isEqualTo("INTERRUPTED");
    var events = org.mockito.ArgumentCaptor.forClass(AuditChange.class);
    verify(audit, times(2)).append(events.capture());
    assertThat(events.getAllValues().getLast().beforeValues()).doesNotContainKey("reason");
    assertThat(events.getAllValues().getLast().afterValues()).containsEntry("reason", "停止確認");
    assertThat(executor.retry(start.id(), "復旧", operator).status()).isEqualTo("SUCCEEDED");
  }

  @Test
  void storeExecutionEstablishesAndClearsScopeAndSnapshotsName() {
    when(handler.platformWide()).thenReturn(false);
    var store = new Store();
    store.setId(3L);
    store.setName("対象店舗");
    when(stores.findById(3L)).thenReturn(Optional.of(store));
    when(handler.execute(any()))
        .thenAnswer(
            call -> {
              assertThat(scope.getStoreId()).isEqualTo(3L);
              throw new IllegalStateException("failure");
            });
    var result =
        executor
            .executeScheduled(
                new TaskCommand(
                    "CHECK",
                    "store",
                    10L,
                    3L,
                    LocalDate.of(2026, 10, 7),
                    LocalDate.of(2026, 10, 7)))
            .execution();
    assertThat(result.storeName()).isEqualTo("対象店舗");
    assertThat(result.status()).isEqualTo("FAILED");
    assertThat(scope.hasStoreId()).isFalse();
  }

  @Test
  void existingTransactionStoreContextAndUnknownTaskAreRefused() {
    scope.setStoreId(1L);
    assertThatThrownBy(() -> executor.executeScheduled(command("nested")))
        .isInstanceOf(ServiceException.class);
    scope.clear();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(() -> executor.executeScheduled(command("nested")))
          .isInstanceOf(ServiceException.class);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    assertThatThrownBy(
            () ->
                executor.executeScheduled(
                    new TaskCommand(
                        "UNKNOWN", "key", 10L, null, LocalDate.now(clock), LocalDate.now(clock))))
        .isInstanceOf(ServiceException.class);
    verify(handler, never()).execute(any());
  }
}
