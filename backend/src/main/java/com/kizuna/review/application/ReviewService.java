package com.kizuna.review.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.review.api.dto.ReviewHistoryResponse;
import com.kizuna.review.api.dto.ReviewPublicResponse;
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
import com.kizuna.review.domain.ReviewPublicView;
import com.kizuna.review.domain.ReviewRecord;
import com.kizuna.review.domain.ReviewRepository;
import com.kizuna.review.domain.ReviewSearch;
import com.kizuna.review.domain.ReviewSummaryView;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.publication.Publication;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

  @Transactional
  public ReviewWriteResponse create(Long storeId, String email, ReviewInput input, String key) {
    var actor = intakeActor(storeId, email, input);
    key = ReviewInput.key(key);
    String fingerprint = ReviewFingerprint.receipt(input);
    var existing = operations.findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), key);
    if (existing.isPresent()) return replay(storeId, existing.get(), fingerprint);
    ReviewInput.requirePast(input.receivedAt(), OffsetDateTime.now(clock));
    var origin = origin(storeId, input);
    var row =
        reviews.saveAndFlush(
            ReviewRecord.receive(
                storeId, input, actor.id(), actor.name(), origin.checkedAt(), origin.version()));
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
    return ReviewWriteResponse.of(response(storeId, row), operation, false);
  }

  @Transactional(readOnly = true)
  public ReviewWriteResponse replayCreate(
      Long storeId, String email, ReviewInput input, String key) {
    var actor = intakeActor(storeId, email, input);
    var operation =
        operations
            .findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), ReviewInput.key(key))
            .orElseThrow(() -> new NotFoundException("口コミの受付結果が見つかりません"));
    return replay(storeId, operation, ReviewFingerprint.receipt(input));
  }

  @Transactional
  public ReviewWriteResponse change(Long storeId, String email, ReviewChange change) {
    var actor = changeActor(storeId, email, change);
    var existing =
        operations.findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), change.key());
    if (existing.isPresent()) return replay(storeId, existing.get(), change.fingerprint());
    var row =
        reviews
            .findByIdAndStoreIdForUpdate(change.reviewId(), storeId)
            .orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
    existing = operations.findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), change.key());
    if (existing.isPresent()) return replay(storeId, existing.get(), change.fingerprint());
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
                    row,
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
                .findByStoreIdAndReviewId(storeId, row.getId())
                .orElseThrow(() -> new IllegalStateException("公開許可の記録が見つかりません"));
        p.revoke(
            change.eventAt(),
            change.reason(),
            actor.id(),
            actor.name(),
            ReviewInput.time(OffsetDateTime.now(clock)));
        permissionId = p.getId();
      }
      case PUBLICATION_CHANGED -> row.changePublication(change.publication());
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
        auditState(before, change.type()),
        auditState(ReviewHistory.Before.of(row), change.type()));
    var operation =
        operations.saveAndFlush(
            ReviewOperation.record(
                actor.id(), change.key(), change.fingerprint(), change.type(), row));
    return ReviewWriteResponse.of(response(storeId, row), operation, false);
  }

  @Transactional(readOnly = true)
  public ReviewWriteResponse replayChange(Long storeId, String email, ReviewChange change) {
    var actor = changeActor(storeId, email, change);
    var operation =
        operations
            .findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), change.key())
            .orElseThrow(() -> new NotFoundException("口コミの操作結果が見つかりません"));
    return replay(storeId, operation, change.fingerprint());
  }

  @Transactional
  public ReviewWriteResponse correct(Long storeId, String email, ReviewCorrection correction) {
    var actor = intakeActor(storeId, email, correction.input());
    var existing =
        operations.findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), correction.key());
    if (existing.isPresent()) return replay(storeId, existing.get(), correction.fingerprint());
    var old =
        reviews
            .findByIdAndStoreIdForUpdate(correction.reviewId(), storeId)
            .orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
    existing =
        operations.findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), correction.key());
    if (existing.isPresent()) return replay(storeId, existing.get(), correction.fingerprint());
    old.requireVersion(correction.version());
    ReviewInput.requirePast(correction.input().receivedAt(), OffsetDateTime.now(clock));
    var origin = origin(storeId, correction.input());
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
        Map.of(
            "status",
            old.getStatus().name(),
            "permission_status",
            old.getPermissionStatus().name(),
            "version",
            old.getVersion().toString()));
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
    return ReviewWriteResponse.of(response(storeId, row), operation, false);
  }

  @Transactional(readOnly = true)
  public ReviewWriteResponse replayCorrection(
      Long storeId, String email, ReviewCorrection correction) {
    var actor = intakeActor(storeId, email, correction.input());
    var operation =
        operations
            .findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), correction.key())
            .orElseThrow(() -> new NotFoundException("訂正結果が見つかりません"));
    return replay(storeId, operation, correction.fingerprint());
  }

  @Transactional(readOnly = true)
  public ReviewResponse detail(Long storeId, String email, String id) {
    actors.require(storeId, email, PermissionCode.REVIEW_VIEW);
    return response(storeId, find(storeId, id));
  }

  @Transactional(readOnly = true)
  public Page<ReviewHistoryResponse> history(
      Long storeId, String email, String id, Pageable pageable) {
    actors.require(storeId, email, PermissionCode.REVIEW_VIEW);
    find(storeId, id);
    Specification<ReviewHistory> spec =
        (root, query, cb) ->
            cb.and(cb.equal(root.get("storeId"), storeId), cb.equal(root.get("reviewId"), id));
    return history
        .findBy(spec, q -> q.as(ReviewHistoryView.class).page(pageable))
        .map(ReviewHistoryResponse::of);
  }

  @Transactional(readOnly = true)
  public Page<ReviewSummaryResponse> list(Long storeId, String email, ReviewSearch search) {
    actors.require(storeId, email, PermissionCode.REVIEW_VIEW);
    return reviews
        .findBy(
            search.specification(storeId),
            q -> q.as(ReviewSummaryView.class).page(search.pageable()))
        .map(r -> ReviewSummaryResponse.of(r, OffsetDateTime.now(clock)));
  }

  @Transactional(readOnly = true)
  public Page<ReviewPublicResponse> listPublic(Long storeId, Pageable pageable) {
    // 並びは公開設定が決める。呼出側の sort は受け取らない
    var page =
        PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Publication.order(ReviewRecord::getPublication, ReviewRecord::getId));
    return reviews
        .findBy(
            ReviewSummaryView.displayed(storeId, OffsetDateTime.now(clock)),
            q -> q.as(ReviewPublicView.class).page(page))
        .map(r -> ReviewPublicResponse.of(r, clock.getZone()));
  }

  private ReviewDetailsView find(Long storeId, String id) {
    return reviews
        .findProjectedByIdAndStoreId(ReviewInput.id(id), storeId)
        .orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
  }

  private AuditActor intakeActor(Long storeId, String email, ReviewInput input) {
    var actor = actors.require(storeId, email, PermissionCode.REVIEW_MANAGE);
    if (input.originOrderId() != null) actors.require(storeId, email, PermissionCode.ORDER_MANAGE);
    return actor;
  }

  private record OriginSnapshot(OffsetDateTime checkedAt, Long version) {}

  private OriginSnapshot origin(Long storeId, ReviewInput input) {
    if (input.originOrderId() == null) return new OriginSnapshot(null, null);
    var facts =
        origins
            .find(storeId, input.originOrderId())
            .orElseThrow(() -> new NotFoundException("関連受注が見つかりません"));
    if (!facts.status().equals("COMPLETED") || facts.completionInvalidated())
      throw new ServiceException("有効な完了受注だけを関連付けできます");
    return new OriginSnapshot(ReviewInput.time(OffsetDateTime.now(clock)), facts.orderVersion());
  }

  private AuditActor changeActor(Long storeId, String email, ReviewChange change) {
    return actors.require(
        storeId,
        email,
        switch (change.type()) {
          case APPROVED, REJECTED, PUBLICATION_CHANGED -> PermissionCode.REVIEW_MODERATE;
          case WITHDRAWN, PERMISSION_GRANTED, PERMISSION_REVOKED -> PermissionCode.REVIEW_MANAGE;
          default -> throw new ServiceException("口コミの操作内容を確認してください");
        });
  }

  private static Map<String, String> auditState(ReviewHistory.Before state, Operation type) {
    var values = new LinkedHashMap<String, String>();
    values.put("status", state.status().name());
    values.put("permission_status", state.permissionStatus().name());
    values.put("version", state.version().toString());
    if (type == Operation.PUBLICATION_CHANGED) {
      var p = state.publication();
      values.put("published", Boolean.toString(p.published()));
      if (p.publishFrom() != null) values.put("publish_from", p.publishFrom().toString());
      if (p.publishUntil() != null) values.put("publish_until", p.publishUntil().toString());
      if (p.displayOrder() != null) values.put("display_order", p.displayOrder().toString());
    }
    return values;
  }

  private ReviewResponse response(Long storeId, ReviewRecord row) {
    return response(storeId, find(storeId, row.getId()));
  }

  private ReviewResponse response(Long storeId, ReviewDetailsView row) {
    return ReviewResponse.of(
        row,
        permissions.findProjectedByStoreIdAndReviewId(storeId, row.getId()).orElse(null),
        OffsetDateTime.now(clock));
  }

  private ReviewWriteResponse replay(Long storeId, ReviewOperation operation, String hash) {
    operation.requireSame(hash);
    var row =
        reviews
            .findProjectedByIdAndStoreId(operation.getReviewId(), storeId)
            .orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
    return ReviewWriteResponse.of(response(storeId, row), operation, true);
  }
}
