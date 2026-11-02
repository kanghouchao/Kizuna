package com.kizuna.review.api;

import com.kizuna.review.api.dto.ReviewWriteResponse;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

final class ReviewWrites {
  private ReviewWrites() {}

  static ResponseEntity<ReviewWriteResponse> write(
      ReviewService service,
      Long storeId,
      Authentication auth,
      ReviewCommand command,
      boolean create) {
    ReviewWriteResponse result;
    try {
      result = service.write(storeId, auth.getName(), command);
    } catch (DataIntegrityViolationException error) {
      if (!IntegrityViolations.violates(error, DbConstraint.UQ_T_REVIEW_OPERATIONS_KEY))
        throw error;
      result = service.replayOnly(storeId, auth.getName(), command);
    }
    return ResponseEntity.status(create && !result.operation().replayed() ? 201 : 200).body(result);
  }
}
