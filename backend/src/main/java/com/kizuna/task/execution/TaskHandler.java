package com.kizuna.task.execution;

import com.kizuna.user.domain.PermissionCode;

/** 処理は同じ DB のトランザクションへ参加し、外部送信は専用の送信基盤へ委譲する。 */
public interface TaskHandler {
  String name();

  PermissionCode permission();

  boolean platformWide();

  long execute(TaskContext context);
}
