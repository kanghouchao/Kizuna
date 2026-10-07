package com.kizuna.task.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_execution_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionAttempt extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private Long requestId;

  @Column(nullable = false, updatable = false)
  private String serviceName;

  @Column(updatable = false)
  private String storeName;

  @Column(nullable = false, updatable = false)
  private int attemptNumber;

  @Column(updatable = false)
  private Long retryOf;

  @Column(nullable = false, updatable = false, length = 20)
  private String origin;

  @Column(updatable = false)
  private Long initiatedBy;

  @Column(nullable = false, updatable = false, length = 500)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ExecutionStatus status;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime startedAt;

  private OffsetDateTime finishedAt;
  private Long processedCount;

  @Column(length = 40)
  private String failureCode;

  public static ExecutionAttempt start(
      Long requestId,
      int attemptNumber,
      Long retryOf,
      String origin,
      Long initiatedBy,
      String reason,
      OffsetDateTime now,
      String serviceName,
      String storeName) {
    if (requestId == null
        || requestId <= 0
        || attemptNumber < 1
        || now == null
        || reason == null
        || reason.isBlank()
        || reason.length() > 500
        || !("MANUAL".equals(origin) || "SCHEDULED".equals(origin) || "RETRY".equals(origin))) {
      throw new ServiceException("実行試行の指定が不正です");
    }
    var attempt = new ExecutionAttempt();
    attempt.requestId = requestId;
    attempt.serviceName = serviceName;
    attempt.storeName = storeName;
    attempt.attemptNumber = attemptNumber;
    attempt.retryOf = retryOf;
    attempt.origin = origin;
    attempt.initiatedBy = initiatedBy;
    attempt.reason = reason.strip();
    attempt.startedAt = now;
    attempt.status = ExecutionStatus.RUNNING;
    return attempt;
  }

  public void succeed(long processedCount, OffsetDateTime now) {
    if (processedCount < 0) throw new ServiceException("処理件数は零以上で指定してください");
    finish(ExecutionStatus.SUCCEEDED, now);
    this.processedCount = processedCount;
  }

  public void fail(String code, OffsetDateTime now) {
    if (!"EXECUTION_FAILED".equals(code) && !"AUTHORIZATION_DENIED".equals(code)) {
      throw new IllegalArgumentException("未定義の実行失敗コードです");
    }
    finish(ExecutionStatus.FAILED, now);
    failureCode = code;
  }

  public void interrupt(OffsetDateTime now) {
    finish(ExecutionStatus.INTERRUPTED, now);
    failureCode = "INTERRUPTED";
  }

  public boolean retryable() {
    return status == ExecutionStatus.FAILED || status == ExecutionStatus.INTERRUPTED;
  }

  private void finish(ExecutionStatus result, OffsetDateTime now) {
    if (status != ExecutionStatus.RUNNING) {
      throw new ConflictException("終了した実行結果は変更できません");
    }
    if (now == null || now.isBefore(startedAt)) {
      throw new ServiceException("実行終了日時が不正です");
    }
    status = result;
    finishedAt = now;
  }
}
