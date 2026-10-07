package com.kizuna.task.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.task.domain.ExecutionAttempt;
import com.kizuna.task.domain.ExecutionAttemptRepository;
import com.kizuna.task.domain.ExecutionRequest;
import com.kizuna.task.domain.ExecutionRequestRepository;
import com.kizuna.task.domain.ExecutionStatus;
import com.kizuna.task.execution.ExecutionResult;
import com.kizuna.task.execution.TaskCommand;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskLifecycle {
  private final ExecutionRequestRepository requests;
  private final ExecutionAttemptRepository attempts;
  private final AuditWriter audit;
  private final Clock clock;
  private final StoreRepository stores;

  public record Started(Long id, boolean created) {}

  public record Locked(ExecutionRequest request, ExecutionAttempt attempt) {}

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Started begin(
      TaskCommand command, AuditActor service, AuditActor operator, String origin) {
    var existing = requests.findByTaskNameAndLogicalKey(command.taskName(), command.logicalKey());
    if (existing.isPresent()) {
      existing.get().requireSame(command);
      return new Started(latest(existing.get().getId()).getId(), false);
    }
    var request = requests.saveAndFlush(ExecutionRequest.create(command));
    var attempt =
        attempts.saveAndFlush(
            ExecutionAttempt.start(
                request.getId(),
                1,
                null,
                origin,
                operator == null ? null : operator.id(),
                "初回実行",
                OffsetDateTime.now(clock),
                service.name(),
                storeName(command.storeId())));
    auditState(request, attempt, operator == null ? service : operator, "TASK_STARTED", Map.of());
    return new Started(attempt.getId(), true);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Started retry(Long id, String reason, AuditActor operator, AuditActor service) {
    requireReason(reason);
    var locked = lockAttempt(id, false);
    var request = locked.request();
    var last = latest(request.getId());
    if (!last.getId().equals(id) || !last.retryable()) {
      throw new ConflictException("最新の失敗または中断した実行だけを再試行できます");
    }
    var attempt =
        attempts.saveAndFlush(
            ExecutionAttempt.start(
                request.getId(),
                last.getAttemptNumber() + 1,
                id,
                "RETRY",
                operator.id(),
                reason,
                OffsetDateTime.now(clock),
                service.name(),
                storeName(request.getStoreId())));
    auditState(request, attempt, operator, "TASK_RETRIED", Map.of("retry_of", id.toString()));
    return new Started(attempt.getId(), true);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ExecutionResult fail(Long id, String code) {
    var locked = lockAttempt(id, false);
    var attempt = locked.attempt();
    var request = locked.request();
    if (attempt.getStatus() == ExecutionStatus.RUNNING) {
      attempt.fail(code, OffsetDateTime.now(clock));
      auditState(
          request,
          attempt,
          serviceActor(request, attempt),
          "TASK_FAILED",
          Map.of("status", "RUNNING"));
    }
    return result(request, attempt);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ExecutionResult interrupt(
      Long id, String reason, AuditActor operator, int minimumAgeSeconds) {
    requireReason(reason);
    var locked = lockAttempt(id, true);
    var attempt = locked.attempt();
    var request = locked.request();
    if (attempt.getStartedAt().plusSeconds(minimumAgeSeconds).isAfter(OffsetDateTime.now(clock))) {
      throw new ConflictException("開始直後の実行は中断として記録できません");
    }
    attempt.interrupt(OffsetDateTime.now(clock));
    audit.append(
        new AuditChange(
            operator,
            request.getStoreId(),
            "TASK_INTERRUPTED",
            "TASK_EXECUTION",
            attempt.getId().toString(),
            "EXECUTION_REQUEST",
            request.getId().toString(),
            Map.of("status", "RUNNING"),
            Map.of("status", "INTERRUPTED", "reason", reason.strip()),
            "INTERRUPTED"));
    return result(request, attempt);
  }

  @Transactional(readOnly = true)
  public ExecutionResult get(Long id) {
    var attempt = requireAttempt(id);
    return result(requireRequest(attempt.getRequestId()), attempt);
  }

  @Transactional(readOnly = true)
  public ExecutionResult replay(TaskCommand command) {
    var request =
        requests
            .findByTaskNameAndLogicalKey(command.taskName(), command.logicalKey())
            .orElseThrow(() -> new ConflictException("実行要求が競合しました。再取得してください"));
    request.requireSame(command);
    return result(request, latest(request.getId()));
  }

  public ExecutionAttempt requireAttempt(Long id) {
    return attempts.findById(id).orElseThrow(() -> new NotFoundException("実行履歴が見つかりません"));
  }

  public ExecutionRequest requireRequest(Long id) {
    return requests.findById(id).orElseThrow(() -> new NotFoundException("実行要求が見つかりません"));
  }

  public ExecutionRequest lockRequest(Long id) {
    return requests.lockById(id).orElseThrow(() -> new NotFoundException("実行要求が見つかりません"));
  }

  // 親行のロック後に試行を読むことで、中断・再試行と競合した古い状態を実行に使わない。
  public Locked lockAttempt(Long id, boolean idleOnly) {
    Long requestId =
        attempts.findRequestId(id).orElseThrow(() -> new NotFoundException("実行履歴が見つかりません"));
    var request =
        (idleOnly ? requests.lockIdleById(requestId) : requests.lockById(requestId))
            .orElseThrow(() -> new NotFoundException("実行要求が見つかりません"));
    return new Locked(request, requireAttempt(id));
  }

  public ExecutionAttempt latest(Long requestId) {
    return attempts
        .findFirstByRequestIdOrderByAttemptNumberDesc(requestId)
        .orElseThrow(() -> new IllegalStateException("実行要求に試行がありません"));
  }

  public void auditState(
      ExecutionRequest request,
      ExecutionAttempt attempt,
      AuditActor actor,
      String action,
      Map<String, String> before) {
    audit.append(
        new AuditChange(
            actor,
            request.getStoreId(),
            action,
            "TASK_EXECUTION",
            attempt.getId().toString(),
            "EXECUTION_REQUEST",
            request.getId().toString(),
            before,
            Map.of(
                "status",
                attempt.getStatus().name(),
                "task_name",
                request.getTaskName(),
                "service_user_id",
                request.getServiceUserId().toString()),
            attempt.getStatus().name()));
  }

  public static AuditActor serviceActor(ExecutionRequest request, ExecutionAttempt attempt) {
    return new AuditActor(request.getServiceUserId(), "SERVICE", attempt.getServiceName());
  }

  public static ExecutionResult result(ExecutionRequest request, ExecutionAttempt attempt) {
    return new ExecutionResult(
        attempt.getId(),
        request.getId(),
        attempt.getAttemptNumber(),
        attempt.getRetryOf(),
        request.getTaskName(),
        request.getLogicalKey(),
        request.getServiceUserId(),
        attempt.getServiceName(),
        request.getStoreId(),
        attempt.getStoreName(),
        request.getPeriodStart(),
        request.getPeriodEnd(),
        attempt.getOrigin(),
        attempt.getInitiatedBy(),
        attempt.getReason(),
        attempt.getStatus().name(),
        attempt.getStartedAt(),
        attempt.getFinishedAt(),
        attempt.getProcessedCount(),
        attempt.getFailureCode());
  }

  private String storeName(Long storeId) {
    return storeId == null
        ? null
        : stores
            .findById(storeId)
            .orElseThrow(() -> new NotFoundException("対象店舗が見つかりません"))
            .getName();
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500 || reason.indexOf('\0') >= 0) {
      throw new ServiceException("理由は 1～500 文字で指定してください");
    }
  }
}
