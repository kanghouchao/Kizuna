package com.kizuna.recruitment.infrastructure;

import com.kizuna.recruitment.application.ApplicantAnonymizationTask;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "app.applicant-anonymization",
    name = "enabled",
    havingValue = "true")
public class ApplicantAnonymizationSchedule {
  private final TaskExecutor executor;
  private final Clock clock;
  private final Long serviceUserId;

  public ApplicantAnonymizationSchedule(
      TaskExecutor executor, Clock clock, AppProperties properties) {
    this.executor = executor;
    this.clock = clock;
    this.serviceUserId = properties.getApplicantAnonymization().getServiceUserId();
    if (serviceUserId == null || serviceUserId <= 0) {
      throw new IllegalArgumentException("匿名化処理にはサービス ID を明示してください");
    }
  }

  @Scheduled(
      cron = "#{@appProperties.applicantAnonymization.cron}",
      zone = "#{@appProperties.timezone}")
  public void anonymizeDaily() {
    LocalDate today = LocalDate.now(clock);
    executor.executeScheduled(
        new TaskCommand(
            ApplicantAnonymizationTask.NAME, today.toString(), serviceUserId, null, today, today));
  }
}
