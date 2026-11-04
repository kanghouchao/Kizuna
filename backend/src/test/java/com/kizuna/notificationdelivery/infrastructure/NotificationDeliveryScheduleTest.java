package com.kizuna.notificationdelivery.infrastructure;

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

class NotificationDeliveryScheduleTest {
  @Test
  void disabledByDefaultAndEnablingRequiresAnExplicitService() {
    new ApplicationContextRunner()
        .withUserConfiguration(NotificationDeliverySchedule.class)
        .run(context -> assertThat(context).doesNotHaveBean(NotificationDeliverySchedule.class));
    assertThatThrownBy(
            () ->
                new NotificationDeliverySchedule(
                    mock(TaskExecutor.class), Clock.systemUTC(), new AppProperties()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void eachRunIsKeyedByItsMinuteAndDatedByTheApplicationCalendar() {
    var executor = mock(TaskExecutor.class);
    var properties = new AppProperties();
    properties.getNotificationDelivery().setServiceUserId(8L);
    new NotificationDeliverySchedule(executor, clock("15:05:00.004"), properties).deliverDue();
    new NotificationDeliverySchedule(executor, clock("15:06:00.002"), properties).deliverDue();
    var day = LocalDate.of(2026, 11, 4);
    verify(executor)
        .executeScheduled(
            new TaskCommand("NOTIFICATION_DELIVER", "2026-11-03T15:05:00Z", 8L, day, day));
    verify(executor)
        .executeScheduled(
            new TaskCommand("NOTIFICATION_DELIVER", "2026-11-03T15:06:00Z", 8L, day, day));
  }

  private static Clock clock(String utcTime) {
    return Clock.fixed(Instant.parse("2026-11-03T" + utcTime + "Z"), ZoneId.of("Asia/Tokyo"));
  }
}
