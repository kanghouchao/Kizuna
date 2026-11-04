package com.kizuna.notificationdelivery.infrastructure;

import com.kizuna.notificationdelivery.application.NotificationDeliveryTask;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.notification-delivery", name = "enabled", havingValue = "true")
public class NotificationDeliverySchedule {
  private final TaskExecutor executor;
  private final Clock clock;
  private final Long serviceUserId;

  public NotificationDeliverySchedule(
      TaskExecutor executor, Clock clock, AppProperties properties) {
    this.executor = executor;
    this.clock = clock;
    this.serviceUserId = properties.getNotificationDelivery().getServiceUserId();
    if (serviceUserId == null || serviceUserId <= 0) {
      throw new IllegalArgumentException("送信処理にはサービス ID を明示してください");
    }
  }

  // 同じ実行キーは再実行されず前回の結果が返るので、日付ではなく起動した分（UTC）をキーにする。
  // 一日に何度も起動するための形で、分より短い間隔の cron は同じ分の起動が一つにまとまる。
  @Scheduled(
      cron = "#{@appProperties.notificationDelivery.cron}",
      zone = "#{@appProperties.timezone}")
  public void deliverDue() {
    String tick = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES).toString();
    LocalDate today = LocalDate.now(clock);
    executor.executeScheduled(
        new TaskCommand(NotificationDeliveryTask.NAME, tick, serviceUserId, today, today));
  }
}
