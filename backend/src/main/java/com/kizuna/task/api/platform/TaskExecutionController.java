package com.kizuna.task.api.platform;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.task.api.dto.ExecutionReasonRequest;
import com.kizuna.task.api.dto.ExecutionSummary;
import com.kizuna.task.api.dto.TaskExecutionRequest;
import com.kizuna.task.api.dto.TaskExecutionResponse;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskQuery;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/task-executions")
@RequiredArgsConstructor
public class TaskExecutionController {
  private final TaskExecutor executor;
  private final TaskLifecycle lifecycle;
  private final TaskQuery query;
  private final ServiceExecutionIdentityService identities;
  private final AppProperties properties;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public CursorPage<ExecutionSummary> list(
      Authentication auth,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size) {
    identities.requireOperator(auth.getName(), PermissionCode.TASK_MANAGE);
    return query.list(cursor, size);
  }

  @GetMapping("/service-identities")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public Page<ServiceExecutionIdentityService.Candidate> candidates(
      Authentication auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    identities.requireOperator(auth.getName(), PermissionCode.TASK_MANAGE);
    return identities.candidates(page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public TaskExecutionResponse get(Authentication auth, @PathVariable Long id) {
    identities.requireOperator(auth.getName(), PermissionCode.TASK_MANAGE);
    return TaskExecutionResponse.of(lifecycle.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public ResponseEntity<TaskExecutionResponse> execute(
      Authentication auth, @Valid @RequestBody TaskExecutionRequest req) {
    var actor = identities.requireOperator(auth.getName(), PermissionCode.TASK_MANAGE);
    var result = executor.execute(req.command(), actor);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(TaskExecutionResponse.of(result.execution()));
  }

  @PostMapping("/{id}/retries")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public ResponseEntity<TaskExecutionResponse> retry(
      Authentication auth, @PathVariable Long id, @Valid @RequestBody ExecutionReasonRequest req) {
    var actor = identities.requireOperator(auth.getName(), PermissionCode.TASK_MANAGE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(TaskExecutionResponse.of(executor.retry(id, req.reason(), actor)));
  }

  @PostMapping("/{id}/interruption")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public TaskExecutionResponse interrupt(
      Authentication auth, @PathVariable Long id, @Valid @RequestBody ExecutionReasonRequest req) {
    var actor = identities.requireOperator(auth.getName(), PermissionCode.TASK_MANAGE);
    try {
      return TaskExecutionResponse.of(
          lifecycle.interrupt(
              id, req.reason(), actor, properties.getTasks().getInterruptionMinimumAgeSeconds()));
    } catch (PessimisticLockingFailureException busy) {
      throw new ConflictException("処理が実行中のため中断として記録できません");
    }
  }
}
