package com.kizuna.task.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.BaseEntity;
import com.kizuna.task.execution.TaskCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_execution_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionRequest extends BaseEntity {
  @Column(nullable = false, updatable = false, length = 60)
  private String taskName;

  @Column(nullable = false, updatable = false, length = 120)
  private String logicalKey;

  @Column(nullable = false, updatable = false)
  private Long serviceUserId;

  @Column(updatable = false)
  private Long storeId;

  @Column(nullable = false, updatable = false)
  private LocalDate periodStart;

  @Column(nullable = false, updatable = false)
  private LocalDate periodEnd;

  public static ExecutionRequest create(TaskCommand command) {
    var request = new ExecutionRequest();
    request.taskName = command.taskName();
    request.logicalKey = command.logicalKey();
    request.serviceUserId = command.serviceUserId();
    request.storeId = command.storeId();
    request.periodStart = command.periodStart();
    request.periodEnd = command.periodEnd();
    return request;
  }

  public TaskCommand command() {
    return new TaskCommand(taskName, logicalKey, serviceUserId, storeId, periodStart, periodEnd);
  }

  public void requireSame(TaskCommand command) {
    if (!Objects.equals(command(), command)) {
      throw new ConflictException("実行キーは異なる処理条件では再利用できません");
    }
  }
}
