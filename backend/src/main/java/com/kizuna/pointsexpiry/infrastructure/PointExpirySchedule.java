package com.kizuna.pointsexpiry.infrastructure;

import com.kizuna.pointsexpiry.application.PointExpiryTask;
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
@ConditionalOnProperty(prefix = "app.points-expiry", name = "enabled", havingValue = "true")
public class PointExpirySchedule {
  private final TaskExecutor executor;
  private final Clock clock;
  private final Long serviceUserId;

  public PointExpirySchedule(TaskExecutor executor, Clock clock, AppProperties properties) {
    this.executor = executor;
    this.clock = clock;
    this.serviceUserId = properties.getPointsExpiry().getServiceUserId();
    if (serviceUserId == null || serviceUserId <= 0) {
      throw new IllegalArgumentException("失効処理にはサービス ID を明示してください");
    }
  }

  @Scheduled(cron = "#{@appProperties.pointsExpiry.cron}", zone = "#{@appProperties.timezone}")
  public void expireDaily() {
    LocalDate today = LocalDate.now(clock);
    executor.executeScheduled(
        new TaskCommand(PointExpiryTask.NAME, today.toString(), serviceUserId, null, today, today));
  }
}
