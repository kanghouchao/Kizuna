package com.kizuna.survey.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.survey.api.dto.SurveyActorResponse;
import com.kizuna.survey.api.dto.SurveyAnswerHistoryResponse;
import com.kizuna.survey.api.dto.SurveyAnswerSummaryResponse;
import com.kizuna.survey.api.dto.SurveyResponse;
import com.kizuna.survey.api.dto.SurveyRevisionHistoryResponse;
import com.kizuna.survey.api.dto.SurveyRevisionSummaryResponse;
import com.kizuna.survey.api.dto.SurveySummaryResponse;
import com.kizuna.survey.domain.SurveyAnswer;
import com.kizuna.survey.domain.SurveyAnswerRepository;
import com.kizuna.survey.domain.SurveyAnswerSummaryView;
import com.kizuna.survey.domain.SurveyHistory;
import com.kizuna.survey.domain.SurveyHistoryRepository;
import com.kizuna.survey.domain.SurveyHistoryView;
import com.kizuna.survey.domain.SurveyInput;
import com.kizuna.survey.domain.SurveyRevision;
import com.kizuna.survey.domain.SurveyRevisionRepository;
import com.kizuna.survey.domain.SurveyRevisionSummaryView;
import com.kizuna.survey.domain.SurveySeries;
import com.kizuna.survey.domain.SurveySeriesRepository;
import com.kizuna.survey.domain.SurveySeriesView;
import com.kizuna.survey.domain.SurveyValues.AnswerStatus;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import com.kizuna.user.domain.PermissionCode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SurveyReadService {
  private final SurveySeriesRepository surveys;
  private final SurveyRevisionRepository revisions;
  private final SurveyAnswerRepository answers;
  private final SurveyHistoryRepository history;
  private final SurveyActors actors;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<SurveySummaryResponse> list(
      String email, int page, int size, String q, String id, RevisionStatus status, String sort) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    var paging = page(page, size, sort, false);
    Specification<SurveySeries> spec = (r, query, cb) -> cb.conjunction();
    if (id != null) {
      var target = SurveyInput.id(id);
      spec = spec.and((r, query, cb) -> cb.equal(r.get("id"), target));
    }
    if (q != null && !q.isBlank() || status != null) {
      String pattern =
          q == null || q.isBlank()
              ? null
              : "%"
                  + SurveyInput.text(q, 120, true)
                      .toLowerCase(Locale.ROOT)
                      .replace("\\", "\\\\")
                      .replace("%", "\\%")
                      .replace("_", "\\_")
                  + "%";
      spec =
          spec.and(
              (r, query, cb) -> {
                var sub = query.subquery(String.class);
                var revision = sub.from(SurveyRevision.class);
                var predicate = cb.equal(revision.get("id"), r.get("latestRevisionId"));
                if (pattern != null)
                  predicate =
                      cb.and(predicate, cb.like(cb.lower(revision.get("title")), pattern, '\\'));
                if (status != null)
                  predicate = cb.and(predicate, cb.equal(revision.get("status"), status));
                sub.select(revision.get("id")).where(predicate);
                return cb.exists(sub);
              });
    }
    var result = surveys.findBy(spec, f -> f.as(SurveySeriesView.class).page(paging));
    if (result.isEmpty())
      return result.map(
          row -> {
            throw new IllegalStateException("アンケートが見つかりません");
          });
    var latest = latest(result.getContent());
    var current = current(result.getContent());
    return result.map(row -> summary(row, latest.get(row.getLatestRevisionId()), current));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public SurveyResponse detail(String email, String id) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    var row = surveys.findProjectedById(SurveyInput.id(id)).orElseThrow(SurveyReadService::missing);
    var summary =
        summary(row, latest(List.of(row)).get(row.getLatestRevisionId()), current(List.of(row)));
    return new SurveyResponse(
        summary.id(),
        summary.latestRevisionId(),
        summary.latestRevisionNumber(),
        summary.latestTitle(),
        summary.latestStatus(),
        summary.openRevisionId(),
        summary.draftRevisionId(),
        summary.createdAt(),
        new SurveyActorResponse(row.getRecordedBy().toString(), row.getRecorderName()),
        "NOT_CONFIGURED");
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<SurveyRevisionSummaryResponse> revisions(
      String email, String sid, int page, int size) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    requireSurvey(sid);
    validatePage(page, size);
    Specification<SurveyRevision> spec = (r, q, cb) -> cb.equal(r.get("surveyId"), sid);
    return revisions
        .findBy(
            spec,
            f ->
                f.as(SurveyRevisionSummaryView.class)
                    .page(
                        PageRequest.of(
                            page, size, Sort.by(Sort.Direction.DESC, "revisionNumber", "id"))))
        .map(SurveyRevisionSummaryResponse::of);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<SurveyAnswerSummaryResponse> answers(
      String email,
      String sid,
      String rid,
      int page,
      int size,
      String id,
      AnswerStatus status,
      String sort) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    requireRevision(sid, rid);
    var paging = page(page, size, sort, true);
    Specification<SurveyAnswer> spec = (r, q, cb) -> cb.equal(r.get("revisionId"), rid);
    if (id != null) {
      var target = SurveyInput.id(id);
      spec = spec.and((r, q, cb) -> cb.equal(r.get("id"), target));
    }
    if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
    return answers
        .findBy(spec, f -> f.as(SurveyAnswerSummaryView.class).page(paging))
        .map(SurveyAnswerSummaryResponse::of);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<SurveyRevisionHistoryResponse> revisionHistory(
      String email, String sid, String rid, String cursor, int size) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    requireRevision(sid, rid);
    return history("revisionId", rid, cursor, size).map(SurveyRevisionHistoryResponse::of);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<SurveyAnswerHistoryResponse> answerHistory(
      String email, String aid, String cursor, int size) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    if (!answers.existsById(SurveyInput.id(aid))) throw missing();
    return history("responseId", aid, cursor, size).map(SurveyAnswerHistoryResponse::of);
  }

  private CursorPage<SurveyHistoryView> history(String target, String id, String cursor, int size) {
    validatePage(0, size);
    Specification<SurveyHistory> spec = (r, q, cb) -> cb.equal(r.get(target), id);
    if (cursor != null) {
      var after = SurveyInput.id(PageCursor.decodeKey(cursor));
      spec = spec.and((r, q, cb) -> cb.lessThan(r.get("id"), after));
    }
    var rows =
        history
            .findBy(
                spec,
                f ->
                    f.as(SurveyHistoryView.class)
                        .page(PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, "id"))))
            .getContent();
    return CursorPage.of(rows, size, r -> PageCursor.encodeKey(r.getId()));
  }

  private Map<String, SurveyRevisionSummaryView> latest(List<SurveySeriesView> rows) {
    return revisions
        .findProjectedByIdIn(rows.stream().map(SurveySeriesView::getLatestRevisionId).toList())
        .stream()
        .collect(Collectors.toMap(SurveyRevisionSummaryView::getId, Function.identity()));
  }

  private List<SurveyRevisionSummaryView> current(Collection<SurveySeriesView> rows) {
    return revisions.findProjectedBySurveyIdInAndStatusIn(
        rows.stream().map(SurveySeriesView::getId).toList(),
        List.of(RevisionStatus.DRAFT, RevisionStatus.OPEN));
  }

  private SurveySummaryResponse summary(
      SurveySeriesView row,
      SurveyRevisionSummaryView latest,
      List<SurveyRevisionSummaryView> current) {
    String open = null, draft = null;
    for (var r : current)
      if (r.getSurveyId().equals(row.getId())) {
        if (r.getStatus() == RevisionStatus.OPEN) open = r.getId();
        else draft = r.getId();
      }
    return new SurveySummaryResponse(
        row.getId(),
        latest.getId(),
        latest.getRevisionNumber(),
        latest.getTitle(),
        latest.getStatus(),
        open,
        draft,
        SurveyInput.time(row.getCreatedAt()));
  }

  private void requireSurvey(String id) {
    if (!surveys.existsById(SurveyInput.id(id))) throw missing();
  }

  private void requireRevision(String sid, String rid) {
    if (!revisions.existsByIdAndSurveyId(SurveyInput.id(rid), SurveyInput.id(sid))) throw missing();
  }

  private static NotFoundException missing() {
    return new NotFoundException("対象のアンケート・設問版・回答が見つかりません");
  }

  private void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw SurveyInput.invalid();
  }

  private PageRequest page(int page, int size, String sort, boolean answer) {
    validatePage(page, size);
    if (sort == null
        || !List.of("CREATED_ASC", "CREATED_DESC").contains(sort)
            && (!answer || !List.of("RECEIVED_ASC", "RECEIVED_DESC").contains(sort)))
      throw SurveyInput.invalid();
    return PageRequest.of(
        page,
        size,
        Sort.by(
            sort.endsWith("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC,
            sort.startsWith("RECEIVED") ? "receivedAt" : "createdAt",
            "id"));
  }
}
