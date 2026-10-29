package com.kizuna.recruitment.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantAnonymizationTarget;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.AttachmentUploadRepository;
import com.kizuna.recruitment.infrastructure.AttachmentObject;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentStorage;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.task.execution.TaskContext;
import com.kizuna.task.execution.TaskHandler;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保持期間を過ぎた終端の応募者を匿名化する。添付の実体を先に消し、DB 側は実行基盤の一つのトランザクションで確定する。
 *
 * <p>途中で失敗すると DB 側は全件巻き戻り、実体だけが消えた応募者は次回の実行で同じく対象になる（実体の不在は成功扱い）。
 */
@Component
@RequiredArgsConstructor
public class ApplicantAnonymizationTask implements TaskHandler {
  public static final String NAME = "APPLICANT_ANONYMIZATION";
  private final ApplicantRepository applicants;
  private final AttachmentUploadRepository uploads;
  private final PrivateAttachmentStorage storage;
  private final SystemConfigService systemConfigs;
  private final ServiceExecutionIdentityService identities;
  private final AuditWriter audit;
  private final AppProperties properties;
  private final Clock clock;

  public String name() {
    return NAME;
  }

  public PermissionCode permission() {
    return PermissionCode.APPLICANT_ANONYMIZE;
  }

  public Optional<PermissionCode> manualPermission() {
    return Optional.of(PermissionCode.APPLICANT_ANONYMIZE);
  }

  public boolean groupWide() {
    return true;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long execute(TaskContext context) {
    LocalDate asOf = context.periodEnd();
    if (context.storeId() != null
        || asOf == null
        || !asOf.equals(context.periodStart())
        || asOf.isAfter(LocalDate.now(clock))) {
      throw new ServiceException("匿名化処理は本日以前の一日を対象に指定してください");
    }
    var actor = requireIdentity(context);
    var retention = systemConfigs.applicantRetentionSettings();
    var periods = new LinkedHashMap<ApplicantStatus, Integer>();
    periods.put(ApplicantStatus.REJECTED, retention.rejectedMonths());
    periods.put(ApplicantStatus.WITHDRAWN, retention.withdrawnMonths());
    periods.put(ApplicantStatus.HIRED, retention.hiredMonths());
    int remaining = properties.getApplicantAnonymization().getMaxApplicants();
    long count = 0;
    for (var period : periods.entrySet()) {
      if (period.getValue() == null || remaining <= 0) continue;
      var targets =
          applicants.findAnonymizationTargets(
              period.getKey(),
              transitionedBefore(asOf, period.getValue(), clock.getZone()),
              Limit.of(remaining));
      for (var target : targets) {
        if (anonymize(target, actor, context)) count++;
      }
      remaining -= targets.size();
    }
    requireIdentity(context);
    return count;
  }

  /** 終端への遷移日 D のうち D + 月数 ≤ 基準日 を満たすものの翌日 0 時。月末の丸めがあるため基準日から月数を引くだけでは求まらない。 */
  static OffsetDateTime transitionedBefore(LocalDate asOf, int months, ZoneId zone) {
    LocalDate last = asOf.minusMonths(months);
    while (!last.plusDays(1).plusMonths(months).isAfter(asOf)) last = last.plusDays(1);
    return last.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
  }

  private boolean anonymize(
      ApplicantAnonymizationTarget target, AuditActor actor, TaskContext context) {
    Applicant applicant =
        applicants
            .findByIdAndStoreIdForUpdate(target.id(), target.storeId())
            .orElseThrow(() -> new IllegalStateException("匿名化対象の応募者が見つかりません"));
    if (applicant.getAnonymizedAt() != null) return false;
    var before = auditValues(applicant);
    var attachments = uploads.findByStoreIdAndApplicantId(target.storeId(), target.id());
    for (AttachmentUpload upload : attachments) storage.delete(object(upload));
    attachments.forEach(uploads::delete);
    applicant.anonymize(OffsetDateTime.now(clock));
    applicant.recordEditor(context.serviceUserId());
    applicants.flush();
    audit.append(
        new AuditChange(
            actor,
            applicant.getStoreId(),
            "APPLICANT_ANONYMIZED",
            "APPLICANT",
            applicant.getId(),
            "TASK_EXECUTION",
            context.executionId().toString(),
            before,
            auditValues(applicant)));
    return true;
  }

  // 消した個人情報を監査へ複製しない。
  private static Map<String, String> auditValues(Applicant applicant) {
    return Map.of(
        "status", applicant.getStatus().name(), "version", String.valueOf(applicant.getVersion()));
  }

  private static AttachmentObject object(AttachmentUpload upload) {
    return new AttachmentObject(
        upload.getObjectId(),
        upload.getMediaType(),
        upload.getSizeBytes(),
        upload.getCanonicalSha256());
  }

  private AuditActor requireIdentity(TaskContext context) {
    identities.requireService(context.serviceUserId(), PermissionCode.TASK_EXECUTE, null);
    return identities.requireService(context.serviceUserId(), permission(), null);
  }
}
