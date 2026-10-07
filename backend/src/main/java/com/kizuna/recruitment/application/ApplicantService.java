package com.kizuna.recruitment.application;

import com.kizuna.recruitment.api.dto.ApplicantHistoryResponse;
import com.kizuna.recruitment.api.dto.ApplicantIntakeRequest;
import com.kizuna.recruitment.api.dto.ApplicantInterviewRequest;
import com.kizuna.recruitment.api.dto.ApplicantMapper;
import com.kizuna.recruitment.api.dto.ApplicantResponse;
import com.kizuna.recruitment.api.dto.ApplicantSummaryResponse;
import com.kizuna.recruitment.api.dto.ApplicantTransitionRequest;
import com.kizuna.recruitment.api.dto.ApplicantUpdateRequest;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import com.kizuna.recruitment.domain.ApplicantStatusHistoryRepository;
import com.kizuna.recruitment.domain.ApplicantSummaryView;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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
  private final PlatformUserRepository users;
  private final ApplicantMapper mapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<ApplicantSummaryResponse> list(
      String search, ApplicantStatus status, Pageable pageable) {
    Specification<Applicant> spec =
        (root, query, cb) -> {
          var conditions = new ArrayList<Predicate>();
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
            Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    return applicants
        .findBy(spec, query -> query.as(ApplicantSummaryView.class).page(bounded))
        .map(mapper::toSummary);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public ApplicantResponse get(String id) {
    return mapper.toResponse(require(id));
  }

  @StoreScoped
  @Transactional
  public ApplicantResponse create(ApplicantIntakeRequest request, String actorEmail) {
    Long actorId = actorId(actorEmail);
    Applicant applicant = Applicant.receive(request.toIntake());
    applicant.recordEditor(actorId);
    applicants.saveAndFlush(applicant);
    record(applicant, null, actorId, "応募受付");
    return mapper.toResponse(applicant);
  }

  @StoreScoped
  @Transactional
  public ApplicantResponse update(String id, ApplicantUpdateRequest request, String actorEmail) {
    Applicant applicant = locked(id, request.version());
    applicant.replaceIntake(request.intake().toIntake());
    applicant.recordEditor(actorId(actorEmail));
    applicants.flush();
    return mapper.toResponse(applicant);
  }

  @StoreScoped
  @Transactional
  public ApplicantResponse interview(
      String id, ApplicantInterviewRequest request, String actorEmail) {
    Applicant applicant = locked(id, request.version());
    applicant.recordInterview(request.toInterview());
    applicant.recordEditor(actorId(actorEmail));
    applicants.flush();
    return mapper.toResponse(applicant);
  }

  @StoreScoped
  @Transactional
  public ApplicantResponse transition(
      String id, ApplicantTransitionRequest request, String actorEmail) {
    Applicant applicant = locked(id, request.version());
    ApplicantStatus previous = applicant.getStatus();
    applicant.transition(request.status(), request.reason());
    Long actorId = actorId(actorEmail);
    applicant.recordEditor(actorId);
    record(applicant, previous, actorId, request.reason().strip());
    applicants.flush();
    return mapper.toResponse(applicant);
  }

  @StoreScoped
  @Transactional
  public ApplicantResponse decide(String id, ApplicantTransitionRequest request) {
    locked(id, request.version());
    if (request.status() != ApplicantStatus.HIRED && request.status() != ApplicantStatus.REJECTED)
      throw new ServiceException("採否の確定には採用または不採用を指定してください");
    throw new ConflictException("選考の最終責任者が未設定のため、採否を確定できません");
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<ApplicantHistoryResponse> history(String id, String cursor, int requestedSize) {
    require(id);
    int size = Math.min(100, CursorPage.clampSize(requestedSize));
    Limit limit = Limit.of(size + 1);
    PageCursor after = cursor == null ? null : PageCursor.decode(cursor);
    var rows =
        after == null
            ? histories.findByApplicantIdOrderByCreatedAtDescIdDesc(id, limit)
            : histories.findAfter(id, after.timestampKey(), after.id(), limit);
    return CursorPage.of(
            rows, size, row -> new PageCursor(row.getCreatedAt().toString(), row.getId()).encode())
        .map(mapper::toHistory);
  }

  private Applicant require(String id) {
    return applicants.findById(id).orElseThrow(() -> new NotFoundException("応募者が見つかりません"));
  }

  private Applicant locked(String id, Long version) {
    Applicant applicant =
        applicants.findScopedForUpdate(id).orElseThrow(() -> new NotFoundException("応募者が見つかりません"));
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
    histories.save(
        ApplicantStatusHistory.builder()
            .applicantId(applicant.getId())
            .actorId(actorId)
            .previousStatus(previous)
            .newStatus(applicant.getStatus())
            .reason(reason)
            .build());
  }
}
