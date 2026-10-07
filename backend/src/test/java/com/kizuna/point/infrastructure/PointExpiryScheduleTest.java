package com.kizuna.point.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kizuna.pointsexpiry.infrastructure.PointExpirySchedule;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PointExpiryScheduleTest {
  @Test
  void disabledByDefaultAndEnablingRequiresAnExplicitService() {
    new ApplicationContextRunner()
        .withUserConfiguration(PointExpirySchedule.class)
        .run(context -> assertThat(context).doesNotHaveBean(PointExpirySchedule.class));
    var properties = new AppProperties();
    assertThatThrownBy(
            () -> new PointExpirySchedule(mock(TaskExecutor.class), Clock.systemUTC(), properties))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void finalDailyAdapterUsesTheApplicationCalendarAndStableLogicalKey() {
    var executor = mock(TaskExecutor.class);
    var properties = new AppProperties();
    properties.getPointsExpiry().setServiceUserId(8L);
    var clock = Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneId.of("Asia/Tokyo"));
    new PointExpirySchedule(executor, clock, properties).expireDaily();
    var day = LocalDate.of(2026, 10, 8);
    verify(executor)
        .executeScheduled(new TaskCommand("POINT_EXPIRY", "2026-10-08", 8L, null, day, day));
  }
}
