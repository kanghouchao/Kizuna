package com.kizuna.survey.api;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.Replays;
import com.kizuna.survey.api.dto.SurveyAnswerCreateRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/surveys")
@RequiredArgsConstructor
public class SurveyController {
  private final SurveyService service;
  private final SurveyReadService reads;

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> create(
      @CurrentStore Long storeId, Authentication auth, @RequestBody SurveyCreateRequest r) {
    return write(storeId, auth, new SurveyCommand.Create(r.definition(), r.dedupeKey()), true);
  }

  @PostMapping("/{sid}/revisions")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> revise(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String sid,
      @RequestBody SurveyRevisionCreateRequest r) {
    return write(
        storeId,
        auth,
        new SurveyCommand.Revise(sid, r.basedOnRevisionId(), r.definition(), r.dedupeKey()),
        true);
  }

  @PutMapping("/{sid}/revisions/{rid}")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> replace(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestBody SurveyRevisionReplaceRequest r) {
    return write(
        storeId,
        auth,
        new SurveyCommand.Replace(sid, rid, r.version(), r.definition(), r.dedupeKey()),
        false);
  }

  @PostMapping("/{sid}/revisions/{rid}/openings")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> open(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestBody SurveyRevisionActionRequest r) {
    return write(
        storeId,
        auth,
        new SurveyCommand.RevisionAction(
            sid, rid, r.version(), Operation.OPENED, r.reason(), r.dedupeKey()),
        false);
  }

  @PostMapping("/{sid}/revisions/{rid}/closures")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_MANAGE')")
  public ResponseEntity<SurveyWriteResponse> close(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestBody SurveyRevisionActionRequest r) {
    return write(
        storeId,
        auth,
        new SurveyCommand.RevisionAction(
            sid, rid, r.version(), Operation.CLOSED, r.reason(), r.dedupeKey()),
        false);
  }

  @PostMapping("/{sid}/revisions/{rid}/responses")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_RECORD')")
  public ResponseEntity<SurveyWriteResponse> receive(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String sid,
      @PathVariable String rid,
      @RequestBody SurveyAnswerCreateRequest r) {
    return write(
        storeId,
        auth,
        new SurveyCommand.Receive(sid, rid, r.revisionVersion(), r.input(), r.dedupeKey()),
        true);
  }

  @GetMapping("/{sid}/revisions/{rid}")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyRevisionResponse revision(
      @CurrentStore Long storeId, @PathVariable String sid, @PathVariable String rid) {
    return SurveyRevisionResponse.of(service.revision(storeId, sid, rid));
  }

  @GetMapping("/{sid}/revisions/{rid}/response-counts")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyResponseCountsResponse counts(
      @CurrentStore Long storeId, @PathVariable String sid, @PathVariable String rid) {
    return SurveyResponseCountsResponse.of(sid, rid, service.counts(storeId, sid, rid));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public Page<SurveySummaryResponse> list(
      @CurrentStore Long storeId,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable,
      @RequestParam(required = false) String q,
      @RequestParam(name = "survey_id", required = false) String surveyId,
      @RequestParam(name = "latest_status", required = false) RevisionStatus latestStatus,
      @RequestParam(defaultValue = "CREATED_DESC") String sort) {
    return reads
        .list(storeId, pageable, q, surveyId, latestStatus, sort)
        .map(SurveySummaryResponse::of);
  }

  @GetMapping("/{sid}")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyResponse survey(@CurrentStore Long storeId, @PathVariable String sid) {
    return SurveyResponse.of(reads.detail(storeId, sid));
  }

  @GetMapping("/{sid}/revisions")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public Page<SurveyRevisionSummaryResponse> revisions(
      @CurrentStore Long storeId,
      @PathVariable String sid,
      @PageableDefault(
              size = 20,
              sort = {"revisionNumber", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return reads.revisions(storeId, sid, pageable).map(SurveyRevisionSummaryResponse::of);
  }

  @GetMapping("/{sid}/revisions/{rid}/responses")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public Page<SurveyAnswerSummaryResponse> responses(
      @CurrentStore Long storeId,
      @PathVariable String sid,
      @PathVariable String rid,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable,
      @RequestParam(name = "response_id", required = false) String responseId,
      @RequestParam(required = false) AnswerStatus status,
      @RequestParam(defaultValue = "RECEIVED_DESC") String sort) {
    return reads
        .answers(storeId, sid, rid, pageable, responseId, status, sort)
        .map(SurveyAnswerSummaryResponse::of);
  }

  @GetMapping("/{sid}/revisions/{rid}/history")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public Page<SurveyRevisionHistoryResponse> revisionHistory(
      @CurrentStore Long storeId,
      @PathVariable String sid,
      @PathVariable String rid,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return reads
        .revisionHistory(storeId, sid, rid, pageable)
        .map(SurveyRevisionHistoryResponse::of);
  }

  private ResponseEntity<SurveyWriteResponse> write(
      Long storeId, Authentication auth, SurveyCommand command, boolean create) {
    var result =
        SurveyWriteResponse.of(
            Replays.write(
                DbConstraint.UQ_T_OPERATION_RECEIPTS_KEY,
                () -> service.write(storeId, auth.getName(), command),
                () -> service.replayOnly(storeId, auth.getName(), command)));
    return ResponseEntity.status(create && !result.operation().replayed() ? 201 : 200).body(result);
  }
}
