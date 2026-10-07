package com.kizuna.pointsexpiry.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.point.application.PointExpiryLedger;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.task.execution.TaskContext;
import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PointExpiryTask implements TaskHandler {
  public static final String NAME = "POINT_EXPIRY";
  private final PointExpiryLedger ledger;
  private final ServiceExecutionIdentityService identities;
  private final AuditWriter audit;
  private final AppProperties properties;
  private final Clock clock;

  public String name() {
    return NAME;
  }

  public PermissionCode permission() {
    return PermissionCode.POINT_EXPIRE;
  }

  public Optional<PermissionCode> manualPermission() {
    return Optional.of(PermissionCode.POINT_EXPIRE);
  }

  public boolean platformWide() {
    return true;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long execute(TaskContext context) {
    LocalDate asOf = context.periodEnd();
    if (context.storeId() != null
        || asOf == null
        || !asOf.equals(context.periodStart())
        || asOf.isAfter(LocalDate.now(clock))) {
      throw new ServiceException("失効処理は本日以前の一日を対象に指定してください");
    }
    requireIdentity(context);
    var entries =
        ledger.materialize(asOf, context.executionId(), properties.getPointsExpiry().getMaxLots());
    for (var entry : entries) {
      var actor = requireIdentity(context);
      audit.append(
          new AuditChange(
              actor,
              null,
              "POINT_EXPIRED",
              "POINT_ENTRY",
              entry.id().toString(),
              "TASK_EXECUTION",
              context.executionId().toString(),
              Map.of(),
              Map.of(
                  "member_id",
                  entry.memberId().toString(),
                  "amount",
                  Integer.toString(entry.amount()),
                  "as_of",
                  asOf.toString())));
    }
    requireIdentity(context);
    return entries.size();
  }

  private AuditActor requireIdentity(TaskContext context) {
    identities.requireService(context.serviceUserId(), PermissionCode.TASK_EXECUTE, null);
    return identities.requireService(context.serviceUserId(), permission(), null);
  }
}
