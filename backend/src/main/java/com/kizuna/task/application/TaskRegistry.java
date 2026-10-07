package com.kizuna.task.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskHandler;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TaskRegistry {
  private final Map<String, TaskHandler> handlers;

  public TaskRegistry(List<TaskHandler> handlers) {
    this.handlers =
        handlers.stream()
            .collect(Collectors.toUnmodifiableMap(TaskHandler::name, Function.identity()));
    if (this.handlers.size() > 32) throw new IllegalStateException("登録処理数の上限を超えています");
  }

  public TaskHandler require(TaskCommand command) {
    var handler = handlers.get(command.taskName());
    if (handler == null || handler.platformWide() != (command.storeId() == null)) {
      throw new ServiceException("登録済み処理とその対象範囲を指定してください");
    }
    return handler;
  }
}
