package com.kizuna.review.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.review.api.dto.ReviewHistoryResponse;
import com.kizuna.review.api.dto.ReviewResponse;
import com.kizuna.review.api.dto.ReviewSummaryResponse;
import com.kizuna.review.api.dto.ReviewWriteResponse;
import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewCorrection;
import com.kizuna.review.domain.ReviewDetailsView;
import com.kizuna.review.domain.ReviewFingerprint;
import com.kizuna.review.domain.ReviewHistory;
import com.kizuna.review.domain.ReviewHistoryRepository;
import com.kizuna.review.domain.ReviewHistoryView;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewOperation;
import com.kizuna.review.domain.ReviewOperationRepository;
import com.kizuna.review.domain.ReviewPermission;
import com.kizuna.review.domain.ReviewPermissionRepository;
import com.kizuna.review.domain.ReviewRecord;
import com.kizuna.review.domain.ReviewRepository;
import com.kizuna.review.domain.ReviewSearch;
import com.kizuna.review.domain.ReviewSummaryView;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {
  private final ReviewRepository reviews;
  private final ReviewOriginLookup origins;
  private final ReviewPermissionRepository permissions;
  private final ReviewHistoryRepository history;
  private final ReviewOperationRepository operations;
  private final ReviewActors actors;
  private final BusinessAudit audit;
  private final Clock clock;

  @StoreScoped
  @Transactional
  public ReviewWriteResponse create(String email, ReviewInput input, String key) {
    var actor = intakeActor(email, input);
    key = ReviewInput.key(key);
    String fingerprint = ReviewFingerprint.receipt(input);
    var existing = operations.findByActorIdAndDedupeKey(actor.id(), key);
    if (existing.isPresent()) return replay(existing.get(), fingerprint);
    ReviewInput.requirePast(input.receivedAt(), OffsetDateTime.now(clock));
    var origin = origin(input);
    var row =
        reviews.saveAndFlush(
            ReviewRecord.receive(
                input, actor.id(), actor.name(), origin.checkedAt(), origin.version()));
    history.save(
        ReviewHistory.record(
            row, Operation.RECEIVED, actor.id(), actor.name(), null, null, null, null));
    audit.record(
        email,
        row.getStoreId(),
        "REVIEW_RECEIVED",
        "REVIEW",
        row.getId(),
        null,
        null,
        Map.of(),
        Map.of("status", "PENDING", "version", row.getVersion().toString()));
    var operation =
        operations.saveAndFlush(
            ReviewOperation.record(actor.id(), key, fingerprint, Operation.RECEIVED, row));
    return ReviewWriteResponse.of(response(row), operation, false);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public ReviewWriteResponse replayCreate(String email, ReviewInput input, String key) {
    var actor = intakeActor(email, input);
    var operation =
        operations
            .findByActorIdAndDedupeKey(actor.id(), ReviewInput.key(key))
            .orElseThrow(() -> new NotFoundException("口コミの受付結果が見つかりません"));
    return replay(operation, ReviewFingerprint.receipt(input));
  }

  @StoreScoped
  @Transactional
  public ReviewWriteResponse change(String email, ReviewChange change) {
    var actor = changeActor(email, change);
    var existing = operations.findByActorIdAndDedupeKey(actor.id(), change.key());
    if (existing.isPresent()) return replay(existing.get(), change.fingerprint());
    var row =
        reviews.lockById(change.reviewId()).orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
    existing = operations.findByActorIdAndDedupeKey(actor.id(), change.key());
    if (existing.isPresent()) return replay(existing.get(), change.fingerprint());
    row.requireVersion(change.version());
    var before = ReviewHistory.Before.of(row);
    String permissionId = null;
    if (change.eventAt() != null)
      ReviewInput.requirePast(change.eventAt(), OffsetDateTime.now(clock));
    switch (change.type()) {
      case APPROVED -> row.approve();
      case REJECTED -> row.reject();
      case WITHDRAWN -> row.withdraw();
      case PERMISSION_GRANTED -> {
        row.grantPermission();
        var p =
            permissions.saveAndFlush(
                ReviewPermission.grant(
                    row.getId(),
                    change.basis(),
                    change.eventAt(),
                    change.evidence(),
                    actor.id(),
                    actor.name()));
        permissionId = p.getId();
      }
      case PERMISSION_REVOKED -> {
        row.revokePermission();
        var p =
            permissions
                .findByReviewId(row.getId())
                .orElseThrow(() -> new IllegalStateException("公開許可の記録が見つかりません"));
        p.revoke(
            change.eventAt(),
            change.reason(),
            actor.id(),
            actor.name(),
            ReviewInput.time(OffsetDateTime.now(clock)));
        permissionId = p.getId();
      }
      default -> throw new ServiceException("口コミの操作内容を確認してください");
    }
    reviews.flush();
    history.save(
        ReviewHistory.record(
            row,
            change.type(),
            actor.id(),
            actor.name(),
            before,
            change.reason(),
            null,
            permissionId));
    audit.record(
        email,
        row.getStoreId(),
        "REVIEW_" + change.type().name(),
        "REVIEW",
        row.getId(),
        null,
        null,
        Map.of(
            "status",
            before.status().name(),
            "permission_status",
            before.permissionStatus().name(),
            "version",
            before.version().toString()),
        Map.of(
            "status",
            row.getStatus().name(),
            "permission_status",
            row.getPermissionStatus().name(),
            "version",
            row.getVersion().toString()));
    var operation =
        operations.saveAndFlush(
            ReviewOperation.record(
                actor.id(), change.key(), change.fingerprint(), change.type(), row));
    return ReviewWriteResponse.of(response(row), operation, false);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public ReviewWriteResponse replayChange(String email, ReviewChange change) {
    var actor = changeActor(email, change);
    var operation =
        operations
            .findByActorIdAndDedupeKey(actor.id(), change.key())
            .orElseThrow(() -> new NotFoundException("口コミの操作結果が見つかりません"));
    return replay(operation, change.fingerprint());
  }

  @StoreScoped
  @Transactional
  public ReviewWriteResponse correct(String email, ReviewCorrection correction) {
    var actor = intakeActor(email, correction.input());
    var existing = operations.findByActorIdAndDedupeKey(actor.id(), correction.key());
    if (existing.isPresent()) return replay(existing.get(), correction.fingerprint());
    var old =
        reviews
            .lockById(correction.reviewId())
            .orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
    existing = operations.findByActorIdAndDedupeKey(actor.id(), correction.key());
    if (existing.isPresent()) return replay(existing.get(), correction.fingerprint());
    old.requireVersion(correction.version());
    ReviewInput.requirePast(correction.input().receivedAt(), OffsetDateTime.now(clock));
    var origin = origin(correction.input());
    var before = ReviewHistory.Before.of(old);
    var row =
        reviews.saveAndFlush(
            ReviewRecord.correction(
                old,
                correction.input(),
                actor.id(),
                actor.name(),
                origin.checkedAt(),
                origin.version()));
    old.linkCorrection(row.getId());
    reviews.flush();
    history.save(
        ReviewHistory.record(
            old,
            Operation.CORRECTION_LINKED,
            actor.id(),
            actor.name(),
            before,
            correction.reason(),
            row.getId(),
            null));
    history.save(
        ReviewHistory.record(
            row,
            Operation.CORRECTION_RECEIVED,
            actor.id(),
            actor.name(),
            null,
            correction.reason(),
            old.getId(),
            null));
    audit.record(
        email,
        old.getStoreId(),
        "REVIEW_CORRECTION_LINKED",
        "REVIEW",
        old.getId(),
        null,
        null,
        Map.of(
            "status",
            before.status().name(),
            "permission_status",
            before.permissionStatus().name(),
            "version",
            before.version().toString()),
        Map.of("status", old.getStatus().name(), "version", old.getVersion().toString()));
    audit.record(
        email,
        row.getStoreId(),
        "REVIEW_CORRECTION_RECEIVED",
        "REVIEW",
        row.getId(),
        null,
        null,
        Map.of(),
        Map.of(
            "status",
            row.getStatus().name(),
            "permission_status",
            row.getPermissionStatus().name(),
            "version",
            row.getVersion().toString()));
    var operation =
        operations.saveAndFlush(
            ReviewOperation.record(
                actor.id(),
                correction.key(),
                correction.fingerprint(),
                Operation.CORRECTION_RECEIVED,
                row));
    return ReviewWriteResponse.of(response(row), operation, false);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public ReviewWriteResponse replayCorrection(String email, ReviewCorrection correction) {
    var actor = intakeActor(email, correction.input());
    var operation =
        operations
            .findByActorIdAndDedupeKey(actor.id(), correction.key())
            .orElseThrow(() -> new NotFoundException("訂正結果が見つかりません"));
    return replay(operation, correction.fingerprint());
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public ReviewResponse detail(String email, String id) {
    actors.require(email, PermissionCode.REVIEW_VIEW);
    return response(find(id));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<ReviewHistoryResponse> history(
      String email, String id, String cursor, int size) {
    actors.require(email, PermissionCode.REVIEW_VIEW);
    find(id);
    if (size < 1 || size > 100) throw new ServiceException("履歴の取得件数は1〜100件で指定してください");
    Specification<ReviewHistory> spec = (root, query, cb) -> cb.equal(root.get("reviewId"), id);
    if (cursor != null) {
      String last = ReviewInput.id(PageCursor.decodeKey(cursor));
      spec = spec.and((root, query, cb) -> cb.lessThan(root.get("id"), last));
    }
    var rows =
        history
            .findBy(
                spec,
                q ->
                    q.as(ReviewHistoryView.class)
                        .page(PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, "id"))))
            .getContent();
    return CursorPage.of(rows, size, h -> PageCursor.encodeKey(h.getId()))
        .map(ReviewHistoryResponse::of);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<ReviewSummaryResponse> list(String email, ReviewSearch search) {
    actors.require(email, PermissionCode.REVIEW_VIEW);
    return reviews
        .findBy(search.specification(), q -> q.as(ReviewSummaryView.class).page(search.pageable()))
        .map(ReviewSummaryResponse::of);
  }

  private ReviewDetailsView find(String id) {
    return reviews
        .findProjectedById(ReviewInput.id(id))
        .orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
  }

  private AuditActor intakeActor(String email, ReviewInput input) {
    var actor = actors.require(email, PermissionCode.REVIEW_MANAGE);
    if (input.originOrderId() != null) actors.require(email, PermissionCode.ORDER_MANAGE);
    return actor;
  }

  private record OriginSnapshot(OffsetDateTime checkedAt, Long version) {}

  private OriginSnapshot origin(ReviewInput input) {
    if (input.originOrderId() == null) return new OriginSnapshot(null, null);
    var facts =
        origins
            .find(input.originOrderId())
            .orElseThrow(() -> new NotFoundException("関連受注が見つかりません"));
    if (!facts.status().equals("COMPLETED") || facts.completionInvalidated())
      throw new ServiceException("有効な完了受注だけを関連付けできます");
    return new OriginSnapshot(ReviewInput.time(OffsetDateTime.now(clock)), facts.orderVersion());
  }

  private AuditActor changeActor(String email, ReviewChange change) {
    return actors.require(
        email,
        switch (change.type()) {
          case APPROVED, REJECTED -> PermissionCode.REVIEW_MODERATE;
          case WITHDRAWN, PERMISSION_GRANTED, PERMISSION_REVOKED -> PermissionCode.REVIEW_MANAGE;
          default -> throw new ServiceException("口コミの操作内容を確認してください");
        });
  }

  private ReviewResponse response(ReviewRecord row) {
    return response(find(row.getId()));
  }

  private ReviewResponse response(ReviewDetailsView row) {
    return ReviewResponse.of(row, permissions.findProjectedByReviewId(row.getId()).orElse(null));
  }

  private ReviewWriteResponse replay(ReviewOperation operation, String hash) {
    operation.requireSame(hash);
    var row =
        reviews
            .findProjectedById(operation.getReviewId())
            .orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
    return ReviewWriteResponse.of(response(row), operation, true);
  }
}
