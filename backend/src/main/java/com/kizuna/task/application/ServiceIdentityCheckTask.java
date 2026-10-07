package com.kizuna.task.application;

import com.kizuna.task.execution.TaskContext;
import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.domain.PermissionCode;
import org.springframework.stereotype.Component;

@Component
public class ServiceIdentityCheckTask implements TaskHandler {
  public String name() {
    return "SERVICE_IDENTITY_CHECK";
  }

  public PermissionCode permission() {
    return PermissionCode.TASK_EXECUTE;
  }

  public boolean platformWide() {
    return true;
  }

  public long execute(TaskContext context) {
    return 0;
  }
}
