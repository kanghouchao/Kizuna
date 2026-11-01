package com.kizuna.recruitment.application;

import com.kizuna.cast.application.CastService;
import com.kizuna.recruitment.api.dto.ApplicantEnrollmentRequest;
import com.kizuna.recruitment.api.dto.ApplicantHistoryResponse;
import com.kizuna.recruitment.api.dto.ApplicantIntakeRequest;
import com.kizuna.recruitment.api.dto.ApplicantInterviewRequest;
import com.kizuna.recruitment.api.dto.ApplicantMapper;
import com.kizuna.recruitment.api.dto.ApplicantResponse;
import com.kizuna.recruitment.api.dto.ApplicantSummaryResponse;
import com.kizuna.recruitment.api.dto.ApplicantTransitionRequest;
import com.kizuna.recruitment.api.dto.ApplicantUpdateRequest;
import com.kizuna.recruitment.api.dto.RecruitmentPolicyResponse;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import com.kizuna.recruitment.domain.ApplicantStatusHistoryRepository;
import com.kizuna.recruitment.domain.ApplicantSummaryView;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicantService {
  private final ApplicantRepository applicants;
  private final ApplicantStatusHistoryRepository histories;
  private final UserRepository users;
  private final ApplicantMapper mapper;
  private final BusinessAudit audit;
  private final SystemConfigService systemConfigs;
  private final CastService casts;

  public RecruitmentPolicyResponse policy() {
    return RecruitmentPolicyResponse.from(systemConfigs.applicantRetentionSettings());
  }

  @Transactional(readOnly = true)
  public Page<ApplicantSummaryResponse> list(
      Long storeId, String search, ApplicantStatus status, Pageable pageable) {
    Specification<Applicant> spec =
        (root, query, cb) -> {
          var conditions = new ArrayList<Predicate>();
          conditions.add(cb.equal(root.get("storeId"), storeId));
          if (search != null && !search.isBlank()) {
            String term = search.strip();
            if (term.length() > 100 || term.indexOf('\0') >= 0)
              throw new ServiceException("検索語は100文字以内で入力してください");
            String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            conditions.add(
                cb.like(
                    cb.lower(root.get("name")),
                    "%" + escaped.toLowerCase(Locale.ROOT) + "%",
                    '\\'));
          }
          if (status != null) conditions.add(cb.equal(root.get("status"), status));
          return cb.and(conditions.toArray(Predicate[]::new));
        };
    var bounded =
        PageRequest.of(
            pageable.getPageNumber(),
            Math.clamp(pageable.getPageSize(), 1, 100),
            Sort.by(Sort.Direction.DESC, Applicant::getCreatedAt, Applicant::getId));
    return applicants
        .findBy(spec, query -> query.as(ApplicantSummaryView.class).page(bounded))
        .map(mapper::toSummary);
  }

  @Transactional(readOnly = true)
  public ApplicantResponse get(Long storeId, String id) {
    return mapper.toResponse(require(storeId, id));
  }

  @Transactional
  public ApplicantResponse create(Long storeId, ApplicantIntakeRequest request, String actorEmail) {
    Long actorId = actorId(actorEmail);
    Applicant applicant = Applicant.receive(storeId, request.toIntake());
    applicant.recordEditor(actorId);
    applicants.saveAndFlush(applicant);
    record(applicant, null, actorId, "応募受付");
    recordAudit(applicant, "APPLICANT_RECEIVED", Map.of());
    return mapper.toResponse(applicant);
  }

  @Transactional
  public ApplicantResponse update(
      Long storeId, String id, ApplicantUpdateRequest request, String actorEmail) {
    Applicant applicant = locked(storeId, id, request.version());
    var before = auditValues(applicant);
    applicant.replaceIntake(request.intake().toIntake());
    applicant.recordEditor(actorId(actorEmail));
    applicants.flush();
    recordAudit(applicant, "APPLICANT_INTAKE_UPDATED", before);
    return mapper.toResponse(applicant);
  }

  @Transactional
  public ApplicantResponse interview(
      Long storeId, String id, ApplicantInterviewRequest request, String actorEmail) {
    Applicant applicant = locked(storeId, id, request.version());
    var before = auditValues(applicant);
    applicant.recordInterview(request.toInterview());
    applicant.recordEditor(actorId(actorEmail));
    applicants.flush();
    recordAudit(applicant, "APPLICANT_INTERVIEW_UPDATED", before);
    return mapper.toResponse(applicant);
  }

  @Transactional
  public ApplicantResponse transition(
      Long storeId, String id, ApplicantTransitionRequest request, String actorEmail) {
    Applicant applicant = locked(storeId, id, request.version());
    var before = auditValues(applicant);
    ApplicantStatus previous = applicant.getStatus();
    applicant.transition(request.status(), request.reason());
    Long actorId = actorId(actorEmail);
    applicant.recordEditor(actorId);
    record(applicant, previous, actorId, request.reason().strip());
    applicants.flush();
    recordAudit(applicant, "APPLICANT_STATUS_CHANGED", before);
    return mapper.toResponse(applicant);
  }

  @Transactional
  public ApplicantResponse decide(
      Long storeId, String id, ApplicantTransitionRequest request, String actorEmail) {
    Applicant applicant = locked(storeId, id, request.version());
    var before = auditValues(applicant);
    ApplicantStatus previous = applicant.getStatus();
    applicant.decide(request.status(), request.reason());
    Long actorId = actorId(actorEmail);
    applicant.recordEditor(actorId);
    record(applicant, previous, actorId, request.reason().strip());
    applicants.flush();
    recordAudit(applicant, "APPLICANT_DECIDED", before);
    return mapper.toResponse(applicant);
  }

  /** 在籍の作成・応募者の更新・双方の監査を一つのトランザクションで確定する。招待は発行しない（在籍側で手動発行する）。 */
  @Transactional
  public ApplicantResponse enroll(
      Long storeId, String id, ApplicantEnrollmentRequest request, String actorEmail) {
    Applicant applicant = locked(storeId, id, request.version());
    applicant.requireEnrollable();
    Long actorId = actorId(actorEmail);
    var before = enrollmentAuditValues(applicant);
    applicant.recordEnrollment(
        casts.create(storeId, request.toCommand(), actorEmail).enrollment().getId());
    applicant.recordEditor(actorId);
    applicants.flush();
    audit.recordCurrent(
        storeId,
        "APPLICANT_ENROLLED",
        "APPLICANT",
        applicant.getId(),
        before,
        enrollmentAuditValues(applicant));
    return mapper.toResponse(applicant);
  }

  @Transactional(readOnly = true)
  public Page<ApplicantHistoryResponse> history(Long storeId, String id, Pageable pageable) {
    require(storeId, id);
    return histories.findByStoreIdAndApplicantId(storeId, id, pageable).map(mapper::toHistory);
  }

  private void recordAudit(Applicant applicant, String action, Map<String, String> before) {
    audit.recordCurrent(
        applicant.getStoreId(),
        action,
        "APPLICANT",
        applicant.getId(),
        before,
        auditValues(applicant));
  }

  // 非公開の連絡先・面接内容・理由を、別の閲覧権限を持つ汎用監査へ複製しない。
  private static Map<String, String> auditValues(Applicant applicant) {
    return Map.of(
        "status", applicant.getStatus().name(), "version", String.valueOf(applicant.getVersion()));
  }

  private static Map<String, String> enrollmentAuditValues(Applicant applicant) {
    return Map.of(
        "enrollment_id",
        Objects.toString(applicant.getEnrollmentId(), ""),
        "version",
        String.valueOf(applicant.getVersion()));
  }

  private Applicant require(Long storeId, String id) {
    return applicants
        .findByIdAndStoreId(id, storeId)
        .orElseThrow(() -> new NotFoundException("応募者が見つかりません"));
  }

  private Applicant locked(Long storeId, String id, Long version) {
    Applicant applicant =
        applicants
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("応募者が見つかりません"));
    applicant.requireVersion(version);
    return applicant;
  }

  private Long actorId(String email) {
    return users
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  private void record(Applicant applicant, ApplicantStatus previous, Long actorId, String reason) {
    var history =
        ApplicantStatusHistory.builder()
            .applicantId(applicant.getId())
            .actorId(actorId)
            .previousStatus(previous)
            .newStatus(applicant.getStatus())
            .reason(reason)
            .build();
    history.setStoreId(applicant.getStoreId());
    histories.save(history);
  }
}
