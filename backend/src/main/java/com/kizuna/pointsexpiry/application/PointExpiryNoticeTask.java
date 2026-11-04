package com.kizuna.pointsexpiry.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.member.application.MemberDisplayNames;
import com.kizuna.notificationdelivery.intake.MemberNotices;
import com.kizuna.point.application.PointExpiryLedger;
import com.kizuna.point.application.PointExpiryLedger.ExpiringBalance;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.task.execution.TaskContext;
import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 対象日から所定の日数後に期限を迎える未消費ポイントを持つ会員へ、会員と期限日ごとに一通の案内を起票する。残高には触れない。 */
@Component
@RequiredArgsConstructor
public class PointExpiryNoticeTask implements TaskHandler {
  public static final String NAME = "POINT_EXPIRY_NOTICE";
  static final String SUBJECT = "ポイントの有効期限のお知らせ";
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");
  private final PointExpiryLedger ledger;
  private final MemberDisplayNames names;
  private final MemberNotices notices;
  private final SystemConfigService systemConfigs;
  private final ServiceExecutionIdentityService identities;
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

  @Transactional(propagation = Propagation.MANDATORY)
  public long execute(TaskContext context) {
    LocalDate asOf = context.periodEnd();
    LocalDate today = LocalDate.now(clock);
    if (asOf == null || !asOf.equals(context.periodStart()) || asOf.isAfter(today)) {
      throw new ServiceException("期限前案内は本日以前の一日を対象に指定してください");
    }
    requireIdentity(context);
    Integer days = systemConfigs.pointExpiryNoticeSettings().days();
    if (days == null) {
      throw new ServiceException("期限前案内の日数をシステム設定で 1 から 365 の整数に設定してください");
    }
    LocalDate expiresOn = asOf.plusDays(days);
    var balances =
        ledger.expiringOn(expiresOn, today, properties.getPointsExpiryNotice().getMaxLots());
    var displayNames = names.of(balances.stream().map(ExpiringBalance::memberId).toList());
    long created = 0;
    for (var balance : balances) {
      var name = displayNames.get(balance.memberId());
      if (name == null) throw new IllegalStateException("台帳の会員が存在しません");
      var notice =
          new MemberNotices.Notice(
              balance.memberId(),
              SUBJECT,
              body(name, balance.points(), expiresOn),
              "point-expiry-notice:" + balance.memberId() + ":" + expiresOn);
      if (notices.queueBusinessEmail(requireIdentity(context), notice)) created++;
    }
    requireIdentity(context);
    return created;
  }

  static String body(String displayName, long points, LocalDate expiresOn) {
    return displayName
        + " 様\n\n"
        + "いつもご利用いただきありがとうございます。\n"
        + "お持ちのポイントのうち "
        + String.format("%,d", points)
        + " ポイントの有効期限は "
        + DATE.format(expiresOn)
        + " です。\n"
        + "期限を過ぎたポイントは失効しますので、お早めにご利用ください。\n";
  }

  private AuditActor requireIdentity(TaskContext context) {
    identities.requireService(context.serviceUserId(), PermissionCode.TASK_EXECUTE);
    return identities.requireService(context.serviceUserId(), permission());
  }
}
