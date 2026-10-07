package com.kizuna.task.execution;

import com.kizuna.user.domain.PermissionCode;
import java.util.Optional;

/** 処理は同じ DB のトランザクションへ参加し、外部送信は専用の送信基盤へ委譲する。 */
public interface TaskHandler {
  String name();

  PermissionCode permission();

  default Optional<PermissionCode> manualPermission() {
    return Optional.empty();
  }

  boolean platformWide();

  long execute(TaskContext context);
}
