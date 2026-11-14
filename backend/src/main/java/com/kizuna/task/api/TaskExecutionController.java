package com.kizuna.task.api;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.task.api.dto.ExecutionReasonRequest;
import com.kizuna.task.api.dto.ExecutionSummary;
import com.kizuna.task.api.dto.TaskExecutionRequest;
import com.kizuna.task.api.dto.TaskExecutionResponse;
import com.kizuna.task.api.dto.UncoveredStoreResponse;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskOptions;
import com.kizuna.task.application.TaskQuery;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
@RequestMapping("/task-executions")
@RequiredArgsConstructor
public class TaskExecutionController {
  private final TaskExecutor executor;
  private final TaskOptions options;
  private final TaskLifecycle lifecycle;
  private final TaskQuery query;
  private final ActorIdentityService identities;
  private final AppProperties properties;
  private final StoreScopeHolder storeScopes;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public Page<ExecutionSummary> list(
      @PageableDefault(
              size = 50,
              sort = {"startedAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return query.list(pageable).map(ExecutionSummary::of);
  }

  @GetMapping("/service-identities")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public Page<ServiceExecutionIdentityService.Candidate> candidates(
      @PageableDefault(
              size = 20,
              sort = {"displayName", "id"})
          Pageable pageable,
      @RequestParam(name = "task_name", defaultValue = "SERVICE_IDENTITY_CHECK") String taskName) {
    return options.candidates(taskName, pageable);
  }

  @GetMapping("/task-types")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public List<TaskOptions.TaskType> taskTypes(Authentication auth) {
    return options.taskTypes(auth.getName());
  }

  @GetMapping("/uncovered-stores")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public List<UncoveredStoreResponse> uncoveredStores() {
    var scope = storeScopes.current();
    if (scope == null) throw new AccessDeniedException("授権店舗集合を解決できません");
    return options.uncoveredStores(scope).stream().map(UncoveredStoreResponse::of).toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public TaskExecutionResponse get(@PathVariable Long id) {
    return TaskExecutionResponse.of(lifecycle.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public ResponseEntity<TaskExecutionResponse> execute(
      Authentication auth, @Valid @RequestBody TaskExecutionRequest req) {
    var actor = identities.requireActor(auth.getName());
    var result = executor.execute(req.command(), actor);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(TaskExecutionResponse.of(result.execution()));
  }

  @PostMapping("/{id}/retries")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public ResponseEntity<TaskExecutionResponse> retry(
      Authentication auth, @PathVariable Long id, @Valid @RequestBody ExecutionReasonRequest req) {
    var actor = identities.requireActor(auth.getName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(TaskExecutionResponse.of(executor.retry(id, req.reason(), actor)));
  }

  @PostMapping("/{id}/interruption")
  @PreAuthorize("hasAuthority('PERM_TASK_MANAGE')")
  public TaskExecutionResponse interrupt(
      Authentication auth, @PathVariable Long id, @Valid @RequestBody ExecutionReasonRequest req) {
    var actor = identities.requireActor(auth.getName());
    try {
      return TaskExecutionResponse.of(
          lifecycle.interrupt(
              id, req.reason(), actor, properties.getTasks().getInterruptionMinimumAgeSeconds()));
    } catch (PessimisticLockingFailureException busy) {
      throw new ConflictException("処理が実行中のため中断として記録できません");
    }
  }
}
