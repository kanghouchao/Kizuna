package com.kizuna.notificationdelivery.application;

import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryAttemptRepository;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.task.execution.TaskContext;
import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryTask implements TaskHandler {
  private final NotificationDeliveryRepository deliveries;
  private final DeliveryAttemptRepository attempts;
  private final ServiceExecutionIdentityService identities;
  private final DeliveryAudit audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Override
  @StoreScopeExempt(reason = "登録処理の識別名だけを返す")
  public String name() {
    return "NOTIFICATION_DELIVER";
  }

  @Override
  @StoreScopeExempt(reason = "登録処理の必要権限だけを返す")
  public PermissionCode permission() {
    return PermissionCode.NOTIFICATION_DELIVER;
  }

  @Override
  @StoreScopeExempt(reason = "手動実行の必要権限だけを返す")
  public Optional<PermissionCode> manualPermission() {
    return Optional.of(PermissionCode.NOTIFICATION_DELIVER);
  }

  @Override
  @StoreScopeExempt(reason = "登録処理の対象範囲だけを返す")
  public boolean platformWide() {
    return false;
  }

  @Override
  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public long execute(TaskContext context) {
    identities.requireService(
        context.serviceUserId(), PermissionCode.TASK_EXECUTE, context.storeId());
    var actor = identities.requireService(context.serviceUserId(), permission(), context.storeId());
    var rows =
        deliveries.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            DeliveryStatus.QUEUED, OffsetDateTime.now(clock), Limit.of(100));
    for (var row : rows) {
      row.dispatch();
      var attempt =
          attempts.saveAndFlush(
              DeliveryAttempt.dispatch(
                  row,
                  context.executionId(),
                  context.serviceUserId(),
                  actor.name(),
                  OffsetDateTime.now(clock)));
      audit.append(row, actor, "NOTIFICATION_DISPATCHED");
      events.publishEvent(
          new DeliveryDispatch(
              row.getId(),
              attempt.getId(),
              context.serviceUserId(),
              context.storeId(),
              context.executionId()));
    }
    return rows.size();
  }
}
