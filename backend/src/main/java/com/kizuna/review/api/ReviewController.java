package com.kizuna.review.api;

import com.kizuna.review.api.dto.ReviewActionRequest;
import com.kizuna.review.api.dto.ReviewCorrectionRequest;
import com.kizuna.review.api.dto.ReviewCreateRequest;
import com.kizuna.review.api.dto.ReviewDecisionRequest;
import com.kizuna.review.api.dto.ReviewHistoryResponse;
import com.kizuna.review.api.dto.ReviewPermissionRequest;
import com.kizuna.review.api.dto.ReviewPermissionRevocationRequest;
import com.kizuna.review.api.dto.ReviewPublicResponse;
import com.kizuna.review.api.dto.ReviewPublicationRequest;
import com.kizuna.review.api.dto.ReviewResponse;
import com.kizuna.review.api.dto.ReviewSummaryResponse;
import com.kizuna.review.api.dto.ReviewWriteResponse;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewSearch;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.annotation.security.PermitAll;
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
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
  private final ReviewService reviews;

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ResponseEntity<ReviewWriteResponse> create(
      @CurrentStore Long storeId, Authentication auth, @RequestBody ReviewCreateRequest request) {
    return ReviewWrites.write(reviews, storeId, auth, request.receipt(), true);
  }

  @PostMapping("/{id}/decisions")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MODERATE')")
  public ResponseEntity<ReviewWriteResponse> decide(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewDecisionRequest request) {
    return ReviewWrites.write(reviews, storeId, auth, request.decide(id), false);
  }

  @PostMapping("/{id}/withdrawals")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ResponseEntity<ReviewWriteResponse> withdraw(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewActionRequest request) {
    return ReviewWrites.write(reviews, storeId, auth, request.withdrawal(id), false);
  }

  @PostMapping("/{id}/permissions")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ResponseEntity<ReviewWriteResponse> grant(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewPermissionRequest request) {
    return ReviewWrites.write(reviews, storeId, auth, request.grant(id), true);
  }

  @PostMapping("/{id}/permission-revocations")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ResponseEntity<ReviewWriteResponse> revoke(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewPermissionRevocationRequest request) {
    return ReviewWrites.write(reviews, storeId, auth, request.revoke(id), false);
  }

  @PutMapping("/{id}/publication")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MODERATE')")
  public ResponseEntity<ReviewWriteResponse> publish(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewPublicationRequest request) {
    return ReviewWrites.write(reviews, storeId, auth, request.publish(id), false);
  }

  @PostMapping("/{id}/corrections")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW') and hasAuthority('PERM_REVIEW_MANAGE')")
  public ResponseEntity<ReviewWriteResponse> correct(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @RequestBody ReviewCorrectionRequest request) {
    return ReviewWrites.write(reviews, storeId, auth, request.correction(id), true);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW')")
  public Page<ReviewSummaryResponse> list(
      @CurrentStore Long storeId,
      Authentication auth,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable,
      @RequestParam(required = false) String q,
      @RequestParam(name = "review_id", required = false) String reviewId,
      @RequestParam(required = false) Status status,
      @RequestParam(name = "permission_status", required = false) PermissionStatus permissionStatus,
      @RequestParam(defaultValue = "RECEIVED_DESC") ReviewSearch.Order sort) {
    return reviews.list(
        storeId,
        auth.getName(),
        new ReviewSearch(pageable, q, reviewId, status, permissionStatus, sort));
  }

  @GetMapping("/public")
  @PermitAll
  public Page<ReviewPublicResponse> listPublic(
      @CurrentStore Long storeId,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return reviews.listPublic(storeId, pageable);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW')")
  public ReviewResponse detail(
      @CurrentStore Long storeId, Authentication auth, @PathVariable String id) {
    return reviews.detail(storeId, auth.getName(), id);
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAuthority('PERM_REVIEW_VIEW')")
  public Page<ReviewHistoryResponse> history(
      @CurrentStore Long storeId,
      Authentication auth,
      @PathVariable String id,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return reviews.history(storeId, auth.getName(), id, pageable);
  }
}
