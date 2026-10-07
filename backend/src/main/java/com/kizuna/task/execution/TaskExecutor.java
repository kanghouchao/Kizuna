package com.kizuna.task.execution;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskRegistry;
import com.kizuna.task.domain.ExecutionStatus;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskExecutor {
  private final TaskRegistry registry;
  private final ServiceExecutionIdentityService identities;
  private final TaskLifecycle lifecycle;
  private final StoreContext storeContext;
  private final Clock clock;
  private final TransactionTemplate work;

  public record Submission(ExecutionResult execution, boolean created) {}

  public TaskExecutor(
      TaskRegistry registry,
      ServiceExecutionIdentityService identities,
      TaskLifecycle lifecycle,
      StoreContext storeContext,
      Clock clock,
      PlatformTransactionManager transactions,
      AppProperties properties) {
    this.registry = registry;
    this.identities = identities;
    this.lifecycle = lifecycle;
    this.storeContext = storeContext;
    this.clock = clock;
    if (properties.getTasks().getTimeoutSeconds() < 1
        || properties.getTasks().getInterruptionMinimumAgeSeconds() < 1) {
      throw new IllegalArgumentException("実行制限時間と中断判定時間は正数で指定してください");
    }
    this.work = new TransactionTemplate(transactions);
    this.work.setTimeout(properties.getTasks().getTimeoutSeconds());
  }

  public Submission execute(TaskCommand command, AuditActor operator) {
    return submit(command, operator, "MANUAL");
  }

  public Submission executeScheduled(TaskCommand command) {
    return submit(command, null, "SCHEDULED");
  }

  private Submission submit(TaskCommand command, AuditActor operator, String origin) {
    requireCleanBoundary();
    var existing = lifecycle.findReplay(command);
    if (existing.isPresent()) return new Submission(existing.get(), false);
    var handler = registry.require(command);
    requireManualPermission(handler, operator);
    var service = requireService(command, handler);
    TaskLifecycle.Started started;
    try {
      started = lifecycle.begin(command, service, operator, origin);
    } catch (DataIntegrityViolationException conflict) {
      if (!IntegrityViolations.violates(conflict, DbConstraint.UQ_T_EXECUTION_REQUESTS_KEY))
        throw conflict;
      return new Submission(lifecycle.replay(command), false);
    }
    return new Submission(
        started.created() ? run(started.id()) : lifecycle.get(started.id()), started.created());
  }

  public ExecutionResult retry(Long id, String reason, AuditActor operator) {
    requireCleanBoundary();
    var original = lifecycle.get(id);
    var command =
        new TaskCommand(
            original.taskName(),
            original.logicalKey(),
            original.serviceUserId(),
            original.storeId(),
            original.periodStart(),
            original.periodEnd());
    var handler = registry.require(command);
    requireManualPermission(handler, operator);
    var service = requireService(command, handler);
    return run(lifecycle.retry(id, reason, operator, service).id());
  }

  private ExecutionResult run(Long id) {
    try {
      return work.execute(
          status -> {
            var locked = lifecycle.lockAttempt(id, false);
            var attempt = locked.attempt();
            var request = locked.request();
            if (attempt.getStatus() != ExecutionStatus.RUNNING
                || !lifecycle.latest(request.getId()).getId().equals(id)) {
              throw new ConflictException("この実行試行は開始できません");
            }
            var handler = registry.require(request.command());
            var service = requireService(request.command(), handler);
            if (request.getStoreId() != null) storeContext.setStoreId(request.getStoreId());
            try {
              long count =
                  handler.execute(
                      new TaskContext(
                          id,
                          service.id(),
                          request.getStoreId(),
                          request.getPeriodStart(),
                          request.getPeriodEnd()));
              attempt.succeed(count, OffsetDateTime.now(clock));
              lifecycle.auditState(
                  request, attempt, service, "TASK_SUCCEEDED", Map.of("status", "RUNNING"));
              return TaskLifecycle.result(request, attempt);
            } finally {
              storeContext.clear();
            }
          });
    } catch (RuntimeException failure) {
      return lifecycle.fail(
          id,
          failure instanceof AccessDeniedException ? "AUTHORIZATION_DENIED" : "EXECUTION_FAILED");
    }
  }

  private void requireManualPermission(TaskHandler handler, AuditActor operator) {
    if (operator != null) {
      handler
          .manualPermission()
          .ifPresent(permission -> identities.requireOperator(operator.id(), permission));
    }
  }

  private AuditActor requireService(TaskCommand command, TaskHandler handler) {
    var service =
        identities.requireService(
            command.serviceUserId(), PermissionCode.TASK_EXECUTE, command.storeId());
    return handler.permission() == PermissionCode.TASK_EXECUTE
        ? service
        : identities.requireService(
            command.serviceUserId(), handler.permission(), command.storeId());
  }

  private void requireCleanBoundary() {
    if (TransactionSynchronizationManager.isActualTransactionActive()
        || storeContext.hasStoreId()) {
      throw new ServiceException("処理実行は既存の取引・店舗コンテキストの外から開始してください");
    }
  }
}
