package com.kizuna.pointsexpiry.infrastructure;

import com.kizuna.pointsexpiry.application.PointExpiryNoticeTask;
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
@ConditionalOnProperty(prefix = "app.points-expiry-notice", name = "enabled", havingValue = "true")
public class PointExpiryNoticeSchedule {
  private final TaskExecutor executor;
  private final Clock clock;
  private final Long serviceUserId;

  public PointExpiryNoticeSchedule(TaskExecutor executor, Clock clock, AppProperties properties) {
    this.executor = executor;
    this.clock = clock;
    this.serviceUserId = properties.getPointsExpiryNotice().getServiceUserId();
    if (serviceUserId == null || serviceUserId <= 0) {
      throw new IllegalArgumentException("期限前案内の起票処理にはサービス ID を明示してください");
    }
  }

  @Scheduled(
      cron = "#{@appProperties.pointsExpiryNotice.cron}",
      zone = "#{@appProperties.timezone}")
  public void noticeDaily() {
    LocalDate today = LocalDate.now(clock);
    executor.executeScheduled(
        new TaskCommand(PointExpiryNoticeTask.NAME, today.toString(), serviceUserId, today, today));
  }
}
