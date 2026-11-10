package com.kizuna.notificationdelivery.application;

import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryAttemptRepository;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.task.execution.TaskContext;
import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
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
  public static final String NAME = "NOTIFICATION_DELIVER";
  private final NotificationDeliveryRepository deliveries;
  private final DeliveryAttemptRepository attempts;
  private final ServiceExecutionIdentityService identities;
  private final DeliveryAudit audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public PermissionCode permission() {
    return PermissionCode.NOTIFICATION_DELIVER;
  }

  @Override
  public Optional<PermissionCode> manualPermission() {
    return Optional.of(PermissionCode.NOTIFICATION_DELIVER);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public long execute(TaskContext context) {
    identities.requireService(context.serviceUserId(), PermissionCode.TASK_EXECUTE);
    var service = identities.requireServiceScope(context.serviceUserId(), permission());
    var actor = service.actor();
    var rows = due(service.stores(), OffsetDateTime.now(clock), Limit.of(100));
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
              row.getId(), attempt.getId(), context.serviceUserId(), context.executionId()));
    }
    return rows.size();
  }

  private List<NotificationDelivery> due(StoreScope stores, OffsetDateTime now, Limit limit) {
    if (stores.allStores())
      return deliveries.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
          DeliveryStatus.QUEUED, now, limit);
    if (stores.storeIds().isEmpty())
      return deliveries
          .findByStatusAndScheduledAtLessThanEqualAndAttributedStoreIdIsNullOrderByScheduledAtAscIdAsc(
              DeliveryStatus.QUEUED, now, limit);
    return deliveries.findDueForStores(DeliveryStatus.QUEUED, now, stores.storeIds(), limit);
  }
}
