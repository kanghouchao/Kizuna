package com.kizuna.survey.api.store;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.survey.api.dto.SurveyWriteResponse;
import com.kizuna.survey.application.SurveyService;
import com.kizuna.survey.domain.SurveyCommand;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

final class SurveyWrites {
  private SurveyWrites() {}

  static ResponseEntity<SurveyWriteResponse> write(
      SurveyService service, Authentication auth, SurveyCommand command, boolean create) {
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
