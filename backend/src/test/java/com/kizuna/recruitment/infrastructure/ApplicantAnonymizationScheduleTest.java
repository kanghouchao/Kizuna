package com.kizuna.recruitment.infrastructure;

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

class ApplicantAnonymizationScheduleTest {
  @Test
  void disabledByDefaultAndEnablingRequiresAnExplicitService() {
    new ApplicationContextRunner()
        .withUserConfiguration(ApplicantAnonymizationSchedule.class)
        .run(context -> assertThat(context).doesNotHaveBean(ApplicantAnonymizationSchedule.class));
    assertThatThrownBy(
            () ->
                new ApplicantAnonymizationSchedule(
                    mock(TaskExecutor.class), Clock.systemUTC(), new AppProperties()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dailyRunUsesTheApplicationCalendarAndStableLogicalKey() {
    var executor = mock(TaskExecutor.class);
    var properties = new AppProperties();
    properties.getApplicantAnonymization().setServiceUserId(8L);
    var clock = Clock.fixed(Instant.parse("2026-10-29T15:30:00Z"), ZoneId.of("Asia/Tokyo"));
    new ApplicantAnonymizationSchedule(executor, clock, properties).anonymizeDaily();
    var day = LocalDate.of(2026, 10, 30);
    verify(executor)
        .executeScheduled(
            new TaskCommand("APPLICANT_ANONYMIZATION", "2026-10-30", 8L, null, day, day));
  }
}
