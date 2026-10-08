package com.kizuna.survey.api.store;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.survey.api.dto.SurveyAnswerActionRequest;
import com.kizuna.survey.api.dto.SurveyAnswerCorrectionRequest;
import com.kizuna.survey.api.dto.SurveyAnswerCreateRequest;
import com.kizuna.survey.api.dto.SurveyAnswerHistoryResponse;
import com.kizuna.survey.api.dto.SurveyAnswerResponse;
import com.kizuna.survey.api.dto.SurveyAnswerSummaryResponse;
import com.kizuna.survey.api.dto.SurveyCreateRequest;
import com.kizuna.survey.api.dto.SurveyResponse;
import com.kizuna.survey.api.dto.SurveyResponseCountsResponse;
import com.kizuna.survey.api.dto.SurveyRevisionActionRequest;
import com.kizuna.survey.api.dto.SurveyRevisionCreateRequest;
import com.kizuna.survey.api.dto.SurveyRevisionHistoryResponse;
import com.kizuna.survey.api.dto.SurveyRevisionReplaceRequest;
import com.kizuna.survey.api.dto.SurveyRevisionResponse;
import com.kizuna.survey.api.dto.SurveyRevisionSummaryResponse;
import com.kizuna.survey.api.dto.SurveySummaryResponse;
import com.kizuna.survey.api.dto.SurveyWriteResponse;
import com.kizuna.survey.application.SurveyReadService;
import com.kizuna.survey.application.SurveyService;
import com.kizuna.survey.domain.SurveyCommand;
import com.kizuna.survey.domain.SurveyValues.AnswerStatus;
import com.kizuna.survey.domain.SurveyValues.Operation;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SurveyController {
  private final SurveyService service;
  private final SurveyReadService reads;

  @PostMapping("/store/surveys")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> create(
      Authentication auth, @RequestBody SurveyCreateRequest r) {
    return write(auth, new SurveyCommand.Create(r.definition(), r.dedupeKey()), true);
  }

  @PostMapping("/store/surveys/{sid}/revisions")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> revise(
      Authentication auth, @PathVariable String sid, @RequestBody SurveyRevisionCreateRequest r) {
    return write(
        auth,
        new SurveyCommand.Revise(sid, r.basedOnRevisionId(), r.definition(), r.dedupeKey()),
        true);
  }

  @PutMapping("/store/surveys/{sid}/revisions/{rid}")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> replace(
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestBody SurveyRevisionReplaceRequest r) {
    return write(
        auth,
        new SurveyCommand.Replace(sid, rid, r.version(), r.definition(), r.dedupeKey()),
        false);
  }

  @PostMapping("/store/surveys/{sid}/revisions/{rid}/openings")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> open(
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestBody SurveyRevisionActionRequest r) {
    return write(
        auth,
        new SurveyCommand.RevisionAction(
            sid, rid, r.version(), Operation.OPENED, r.reason(), r.dedupeKey()),
        false);
  }

  @PostMapping("/store/surveys/{sid}/revisions/{rid}/closures")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> close(
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestBody SurveyRevisionActionRequest r) {
    return write(
        auth,
        new SurveyCommand.RevisionAction(
            sid, rid, r.version(), Operation.CLOSED, r.reason(), r.dedupeKey()),
        false);
  }

  @PostMapping("/store/surveys/{sid}/revisions/{rid}/responses")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_RECORD')")
  public ResponseEntity<SurveyWriteResponse> receive(
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestBody SurveyAnswerCreateRequest r) {
    return write(
        auth,
        new SurveyCommand.Receive(sid, rid, r.revisionVersion(), r.input(), r.dedupeKey()),
        true);
  }

  @PostMapping("/store/survey-responses/{aid}/withdrawals")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_RECORD')")
  public ResponseEntity<SurveyWriteResponse> withdraw(
      Authentication auth, @PathVariable String aid, @RequestBody SurveyAnswerActionRequest r) {
    return write(
        auth, new SurveyCommand.Withdraw(aid, r.version(), r.reason(), r.dedupeKey()), false);
  }

  @PostMapping("/store/survey-responses/{aid}/corrections")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_RECORD')")
  public ResponseEntity<SurveyWriteResponse> correct(
      Authentication auth, @PathVariable String aid, @RequestBody SurveyAnswerCorrectionRequest r) {
    return write(
        auth,
        new SurveyCommand.Correct(aid, r.version(), r.reason(), r.input(), r.dedupeKey()),
        true);
  }

  @GetMapping("/store/surveys/{sid}/revisions/{rid}")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyRevisionResponse revision(
      Authentication auth, @PathVariable String sid, @PathVariable String rid) {
    return service.revision(auth.getName(), sid, rid);
  }

  @GetMapping("/store/survey-responses/{aid}")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyAnswerResponse answer(Authentication auth, @PathVariable String aid) {
    return service.answer(auth.getName(), aid);
  }

  @GetMapping("/store/surveys/{sid}/revisions/{rid}/response-counts")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyResponseCountsResponse counts(
      Authentication auth, @PathVariable String sid, @PathVariable String rid) {
    return service.counts(auth.getName(), sid, rid);
  }

  @GetMapping("/store/surveys")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public Page<SurveySummaryResponse> list(
      Authentication auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(name = "survey_id", required = false) String surveyId,
      @RequestParam(name = "latest_status", required = false) RevisionStatus latestStatus,
      @RequestParam(defaultValue = "CREATED_DESC") String sort) {
    return reads.list(auth.getName(), page, size, q, surveyId, latestStatus, sort);
  }

  @GetMapping("/store/surveys/{sid}")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyResponse survey(Authentication auth, @PathVariable String sid) {
    return reads.detail(auth.getName(), sid);
  }

  @GetMapping("/store/surveys/{sid}/revisions")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public Page<SurveyRevisionSummaryResponse> revisions(
      Authentication auth,
      @PathVariable String sid,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return reads.revisions(auth.getName(), sid, page, size);
  }

  @GetMapping("/store/surveys/{sid}/revisions/{rid}/responses")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public Page<SurveyAnswerSummaryResponse> responses(
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(name = "response_id", required = false) String responseId,
      @RequestParam(required = false) AnswerStatus status,
      @RequestParam(defaultValue = "RECEIVED_DESC") String sort) {
    return reads.answers(auth.getName(), sid, rid, page, size, responseId, status, sort);
  }

  @GetMapping("/store/surveys/{sid}/revisions/{rid}/history")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public CursorPage<SurveyRevisionHistoryResponse> revisionHistory(
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return reads.revisionHistory(auth.getName(), sid, rid, cursor, size);
  }

  @GetMapping("/store/survey-responses/{aid}/history")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public CursorPage<SurveyAnswerHistoryResponse> answerHistory(
      Authentication auth,
      @PathVariable String aid,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return reads.answerHistory(auth.getName(), aid, cursor, size);
  }

  private ResponseEntity<SurveyWriteResponse> write(
      Authentication auth, SurveyCommand command, boolean create) {
    SurveyWriteResponse result;
    try {
      result = service.write(auth.getName(), command);
    } catch (DataIntegrityViolationException error) {
      if (!IntegrityViolations.violates(error, DbConstraint.UQ_T_SURVEY_OPERATIONS_KEY))
        throw error;
      result = service.replayOnly(auth.getName(), command);
    }
    return ResponseEntity.status(create && !result.operation().replayed() ? 201 : 200).body(result);
  }
}
