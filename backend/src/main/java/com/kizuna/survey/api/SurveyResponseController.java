package com.kizuna.survey.api;

import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.survey.api.dto.SurveyAnswerActionRequest;
import com.kizuna.survey.api.dto.SurveyAnswerCorrectionRequest;
import com.kizuna.survey.api.dto.SurveyAnswerHistoryResponse;
import com.kizuna.survey.api.dto.SurveyAnswerResponse;
import com.kizuna.survey.api.dto.SurveyWriteResponse;
import com.kizuna.survey.application.SurveyReadService;
import com.kizuna.survey.application.SurveyService;
import com.kizuna.survey.domain.SurveyCommand;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/survey-responses/{aid}")
@RequiredArgsConstructor
public class SurveyResponseController {
  private final SurveyService service;
  private final SurveyReadService reads;

  @PostMapping("/withdrawals")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_RECORD')")
  public ResponseEntity<SurveyWriteResponse> withdraw(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String aid,
      @RequestBody SurveyAnswerActionRequest r) {
    return SurveyWrites.write(
        service,
        storeId,
        auth,
        new SurveyCommand.Withdraw(aid, r.version(), r.reason(), r.dedupeKey()),
        false);
  }

  @PostMapping("/corrections")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_RECORD')")
  public ResponseEntity<SurveyWriteResponse> correct(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String aid,
      @RequestBody SurveyAnswerCorrectionRequest r) {
    return SurveyWrites.write(
        service,
        storeId,
        auth,
        new SurveyCommand.Correct(aid, r.version(), r.reason(), r.input(), r.dedupeKey()),
        true);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyAnswerResponse answer(
      @CurrentStore Long storeId, Authentication auth, @PathVariable String aid) {
    return service.answer(storeId, auth.getName(), aid);
  }

  @GetMapping("/history")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public Page<SurveyAnswerHistoryResponse> answerHistory(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String aid,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return reads.answerHistory(storeId, auth.getName(), aid, pageable);
  }
}
