package com.kizuna.pointsexpiry.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PointExpiryNoticeScheduleTest {
  @Test
  void disabledByDefaultAndEnablingRequiresAnExplicitService() {
    new ApplicationContextRunner()
        .withUserConfiguration(PointExpiryNoticeSchedule.class)
        .run(context -> assertThat(context).doesNotHaveBean(PointExpiryNoticeSchedule.class));
    assertThatThrownBy(
            () ->
                new PointExpiryNoticeSchedule(
                    mock(TaskExecutor.class), Clock.systemUTC(), new AppProperties()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dailyRunUsesTheApplicationCalendarAndStableLogicalKey() {
    var executor = mock(TaskExecutor.class);
    var properties = new AppProperties();
    properties.getPointsExpiryNotice().setServiceUserId(8L);
    var clock = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("Asia/Tokyo"));
    new PointExpiryNoticeSchedule(executor, clock, properties).noticeDaily();
    var day = LocalDate.of(2026, 11, 4);
    verify(executor)
        .executeScheduled(new TaskCommand("POINT_EXPIRY_NOTICE", "2026-11-04", 8L, day, day));
  }
}
