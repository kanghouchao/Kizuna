package com.kizuna.review.api;

import com.kizuna.review.api.dto.ReviewActionRequest;
import com.kizuna.review.api.dto.ReviewCorrectionRequest;
import com.kizuna.review.api.dto.ReviewCreateRequest;
import com.kizuna.review.api.dto.ReviewDecisionRequest;
import com.kizuna.review.api.dto.ReviewHistoryResponse;
import com.kizuna.review.api.dto.ReviewPermissionRequest;
import com.kizuna.review.api.dto.ReviewPermissionRevocationRequest;
import com.kizuna.review.api.dto.ReviewPublicationRequest;
import com.kizuna.review.api.dto.ReviewResponse;
import com.kizuna.review.api.dto.ReviewSummaryResponse;
import com.kizuna.review.api.dto.ReviewWriteResponse;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewSearch;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.CursorPage;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
  private final ReviewService reviews;

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ResponseEntity<ReviewWriteResponse> create(
      @CurrentStore Long storeId, Authentication auth, @RequestBody ReviewCreateRequest request) {
    var input = request.input();
    ReviewWriteResponse result;
    try {
      result = reviews.create(storeId, auth.getName(), input, request.dedupeKey());
    } catch (DataIntegrityViolationException conflict) {
      if (!IntegrityViolations.violates(conflict, DbConstraint.UQ_T_REVIEW_OPERATIONS_KEY))
        throw conflict;
      result = reviews.replayCreate(storeId, auth.getName(), input, request.dedupeKey());
    }
    return ResponseEntity.status(result.operation().replayed() ? 200 : 201).body(result);
  }

  @PostMapping("/{id}/decisions")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MODERATE')")
  public ReviewWriteResponse decide(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewDecisionRequest request) {
    return change(storeId, auth.getName(), request.change(id));
  }

  @PostMapping("/{id}/withdrawals")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ReviewWriteResponse withdraw(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewActionRequest request) {
    return change(storeId, auth.getName(), request.withdrawal(id));
  }

  @PostMapping("/{id}/permissions")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ResponseEntity<ReviewWriteResponse> grant(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewPermissionRequest request) {
    var result = change(storeId, auth.getName(), request.change(id));
    return ResponseEntity.status(result.operation().replayed() ? 200 : 201).body(result);
  }

  @PostMapping("/{id}/permission-revocations")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ReviewWriteResponse revoke(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewPermissionRevocationRequest request) {
    return change(storeId, auth.getName(), request.change(id));
  }

  @PutMapping("/{id}/publication")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MODERATE')")
  public ReviewWriteResponse publish(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewPublicationRequest request) {
    return change(storeId, auth.getName(), request.change(id));
  }

  @PostMapping("/{id}/corrections")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ResponseEntity<ReviewWriteResponse> correct(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewCorrectionRequest request) {
    var correction = request.correction(id);
    ReviewWriteResponse result;
    try {
      result = reviews.correct(storeId, auth.getName(), correction);
    } catch (DataIntegrityViolationException conflict) {
      if (!IntegrityViolations.violates(conflict, DbConstraint.UQ_T_REVIEW_OPERATIONS_KEY))
        throw conflict;
      result = reviews.replayCorrection(storeId, auth.getName(), correction);
    }
    return ResponseEntity.status(result.operation().replayed() ? 200 : 201).body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW')")
  public Page<ReviewSummaryResponse> list(
      @CurrentStore Long storeId,
      Authentication auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(name = "review_id", required = false) String reviewId,
      @RequestParam(required = false) Status status,
      @RequestParam(name = "permission_status", required = false) PermissionStatus permissionStatus,
      @RequestParam(defaultValue = "RECEIVED_DESC") ReviewSearch.Order sort) {
    return reviews.list(
        storeId,
        auth.getName(),
        new ReviewSearch(page, size, q, reviewId, status, permissionStatus, sort));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW')")
  public ReviewResponse detail(
      @CurrentStore Long storeId, Authentication auth, @PathVariable String id) {
    return reviews.detail(storeId, auth.getName(), id);
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW')")
  public CursorPage<ReviewHistoryResponse> history(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return reviews.history(storeId, auth.getName(), id, cursor, size);
  }

  private ReviewWriteResponse change(Long storeId, String email, ReviewChange change) {
    try {
      return reviews.change(storeId, email, change);
    } catch (DataIntegrityViolationException conflict) {
      if (!IntegrityViolations.violates(conflict, DbConstraint.UQ_T_REVIEW_OPERATIONS_KEY))
        throw conflict;
      return reviews.replayChange(storeId, email, change);
    }
  }
}
