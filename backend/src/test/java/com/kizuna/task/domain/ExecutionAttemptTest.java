package com.kizuna.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ConflictException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ExecutionAttemptTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T10:00:00+09:00");

  @Test
  void completedWorkCannotBeRewrittenAsFailure() {
    var attempt = ExecutionAttempt.start(11L, 1, null, "MANUAL", 21L, "初回実行", NOW, "定期処理", null);
    attempt.succeed(3, NOW.plusSeconds(1));
    assertThat(attempt.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    assertThat(attempt.getProcessedCount()).isEqualTo(3);
    assertThatThrownBy(() -> attempt.fail("EXECUTION_FAILED", NOW.plusSeconds(2)))
        .isInstanceOf(ConflictException.class);
  }
}
