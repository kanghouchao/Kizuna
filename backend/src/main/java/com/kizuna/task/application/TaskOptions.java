package com.kizuna.task.application;

import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskOptions {
  private final TaskRegistry registry;
  private final ServiceExecutionIdentityService identities;

  public record TaskType(String name, boolean manualAllowed) {}

  public List<TaskType> taskTypes(String email) {
    return registry.registered().stream()
        .map(handler -> new TaskType(handler.name(), allowed(email, handler)))
        .toList();
  }

  private boolean allowed(String email, TaskHandler handler) {
    try {
      handler
          .manualPermission()
          .ifPresent(permission -> identities.requireOperator(email, permission));
      return true;
    } catch (AccessDeniedException denied) {
      return false;
    }
  }

  public Page<ServiceExecutionIdentityService.Candidate> candidates(
      String taskName, Pageable pageable) {
    return identities.candidates(pageable, registry.requireName(taskName).permission());
  }
}
