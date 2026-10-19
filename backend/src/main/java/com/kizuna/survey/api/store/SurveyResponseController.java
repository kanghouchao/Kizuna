package com.kizuna.survey.api.store;

import com.kizuna.shared.web.CursorPage;
import com.kizuna.survey.api.dto.SurveyAnswerActionRequest;
import com.kizuna.survey.api.dto.SurveyAnswerCorrectionRequest;
import com.kizuna.survey.api.dto.SurveyAnswerHistoryResponse;
import com.kizuna.survey.api.dto.SurveyAnswerResponse;
import com.kizuna.survey.api.dto.SurveyWriteResponse;
import com.kizuna.survey.application.SurveyReadService;
import com.kizuna.survey.application.SurveyService;
import com.kizuna.survey.domain.SurveyCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/survey-responses/{aid}")
@RequiredArgsConstructor
public class SurveyResponseController {
  private final SurveyService service;
  private final SurveyReadService reads;

  @PostMapping("/withdrawals")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_RECORD')")
  public ResponseEntity<SurveyWriteResponse> withdraw(
      Authentication auth, @PathVariable String aid, @RequestBody SurveyAnswerActionRequest r) {
    return SurveyWrites.write(
        service,
        auth,
        new SurveyCommand.Withdraw(aid, r.version(), r.reason(), r.dedupeKey()),
        false);
  }

  @PostMapping("/corrections")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW') and hasAuthority('PERM_SURVEY_RECORD')")
  public ResponseEntity<SurveyWriteResponse> correct(
      Authentication auth, @PathVariable String aid, @RequestBody SurveyAnswerCorrectionRequest r) {
    return SurveyWrites.write(
        service,
        auth,
        new SurveyCommand.Correct(aid, r.version(), r.reason(), r.input(), r.dedupeKey()),
        true);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public SurveyAnswerResponse answer(Authentication auth, @PathVariable String aid) {
    return service.answer(auth.getName(), aid);
  }

  @GetMapping("/history")
  @PreAuthorize("hasAuthority('PERM_SURVEY_VIEW')")
  public CursorPage<SurveyAnswerHistoryResponse> answerHistory(
      Authentication auth,
      @PathVariable String aid,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return reads.answerHistory(auth.getName(), aid, cursor, size);
  }
}
