package com.kizuna.review.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.review.api.dto.ReviewHistoryResponse;
import com.kizuna.review.api.dto.ReviewPublicResponse;
import com.kizuna.review.api.dto.ReviewResponse;
import com.kizuna.review.api.dto.ReviewSummaryResponse;
import com.kizuna.review.api.dto.ReviewWriteResponse;
import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.review.domain.ReviewDetailsView;
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
  public ReviewWriteResponse write(Long storeId, String email, ReviewCommand command) {
    var actor = actor(storeId, email, command);
    var target = lock(storeId, command);
    var old = operations.findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), command.key());
    if (old.isPresent()) return replay(storeId, old.get(), command.fingerprint());
    var result =
        switch (command) {
          case ReviewCommand.Receive c -> receive(storeId, email, actor, c);
          case ReviewCommand.Decide c -> decide(email, actor, target, c);
          case ReviewCommand.Withdraw c -> withdraw(email, actor, target, c);
          case ReviewCommand.Grant c -> grant(email, actor, target, c);
          case ReviewCommand.Revoke c -> revoke(storeId, email, actor, target, c);
          case ReviewCommand.Publish c -> publish(email, actor, target, c);
          case ReviewCommand.Correct c -> correct(storeId, email, actor, target, c);
        };
    var operation =
        operations.saveAndFlush(
            ReviewOperation.record(
                actor.id(), command.key(), command.fingerprint(), result.type(), result.row()));
    return ReviewWriteResponse.of(response(storeId, result.row()), operation, false);
  }

  @Transactional(readOnly = true)
  public ReviewWriteResponse replayOnly(Long storeId, String email, ReviewCommand command) {
    var actor = actor(storeId, email, command);
    var operation =
        operations
            .findByStoreIdAndActorIdAndDedupeKey(storeId, actor.id(), command.key())
            .orElseThrow(() -> new NotFoundException("口コミの操作結果が見つかりません"));
    return replay(storeId, operation, command.fingerprint());
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

  private record Result(ReviewRecord row, Operation type) {}

  private Result receive(Long storeId, String email, AuditActor actor, ReviewCommand.Receive c) {
    ReviewInput.requirePast(c.input().receivedAt(), now());
    var origin = origin(storeId, c.input());
    var row =
        reviews.saveAndFlush(
            ReviewRecord.receive(
                storeId,
                c.input(),
                actor.id(),
                actor.name(),
                origin.checkedAt(),
                origin.version()));
    record(email, actor, row, Operation.RECEIVED, null, null, null, null);
    return new Result(row, Operation.RECEIVED);
  }

  private Result decide(String email, AuditActor actor, ReviewRecord row, ReviewCommand.Decide c) {
    var before = before(row, c.version());
    if (c.type() == Operation.APPROVED) row.approve();
    else row.reject();
    return changed(email, actor, row, c.type(), before, c.reason(), null);
  }

  private Result withdraw(
      String email, AuditActor actor, ReviewRecord row, ReviewCommand.Withdraw c) {
    var before = before(row, c.version());
    row.withdraw();
    return changed(email, actor, row, Operation.WITHDRAWN, before, c.reason(), null);
  }

  private Result grant(String email, AuditActor actor, ReviewRecord row, ReviewCommand.Grant c) {
    var before = before(row, c.version());
    ReviewInput.requirePast(c.grantedAt(), now());
    row.grantPermission();
    var permission =
        permissions.saveAndFlush(
            ReviewPermission.grant(
                row, c.basis(), c.grantedAt(), c.evidence(), actor.id(), actor.name()));
    return changed(
        email, actor, row, Operation.PERMISSION_GRANTED, before, null, permission.getId());
  }

  private Result revoke(
      Long storeId, String email, AuditActor actor, ReviewRecord row, ReviewCommand.Revoke c) {
    var before = before(row, c.version());
    ReviewInput.requirePast(c.receivedAt(), now());
    row.revokePermission();
    var permission =
        permissions
            .findByStoreIdAndReviewId(storeId, row.getId())
            .orElseThrow(() -> new IllegalStateException("公開許可の記録が見つかりません"));
    permission.revoke(
        c.receivedAt(), c.reason(), actor.id(), actor.name(), ReviewInput.time(now()));
    return changed(
        email, actor, row, Operation.PERMISSION_REVOKED, before, c.reason(), permission.getId());
  }

  private Result publish(
      String email, AuditActor actor, ReviewRecord row, ReviewCommand.Publish c) {
    var before = before(row, c.version());
    row.changePublication(c.publication());
    return changed(email, actor, row, Operation.PUBLICATION_CHANGED, before, null, null);
  }

  private Result correct(
      Long storeId, String email, AuditActor actor, ReviewRecord old, ReviewCommand.Correct c) {
    var before = before(old, c.version());
    ReviewInput.requirePast(c.input().receivedAt(), now());
    var origin = origin(storeId, c.input());
    var row =
        reviews.saveAndFlush(
            ReviewRecord.correction(
                old, c.input(), actor.id(), actor.name(), origin.checkedAt(), origin.version()));
    old.linkCorrection(row.getId());
    reviews.flush();
    record(email, actor, old, Operation.CORRECTION_LINKED, before, c.reason(), row.getId(), null);
    record(email, actor, row, Operation.CORRECTION_RECEIVED, null, c.reason(), old.getId(), null);
    return new Result(row, Operation.CORRECTION_RECEIVED);
  }

  private AuditActor actor(Long storeId, String email, ReviewCommand command) {
    AuditActor actor = null;
    for (var permission : command.permissions()) actor = actors.require(storeId, email, permission);
    return actor;
  }

  private ReviewRecord lock(Long storeId, ReviewCommand command) {
    var id =
        switch (command) {
          case ReviewCommand.Receive _ -> null;
          case ReviewCommand.Decide c -> c.reviewId();
          case ReviewCommand.Withdraw c -> c.reviewId();
          case ReviewCommand.Grant c -> c.reviewId();
          case ReviewCommand.Revoke c -> c.reviewId();
          case ReviewCommand.Publish c -> c.reviewId();
          case ReviewCommand.Correct c -> c.reviewId();
        };
    if (id == null) return null;
    return reviews
        .findByIdAndStoreIdForUpdate(id, storeId)
        .orElseThrow(() -> new NotFoundException("口コミが見つかりません"));
  }

  private static ReviewHistory.Before before(ReviewRecord row, Long version) {
    row.requireVersion(version);
    return ReviewHistory.Before.of(row);
  }

  private Result changed(
      String email,
      AuditActor actor,
      ReviewRecord row,
      Operation type,
      ReviewHistory.Before before,
      String reason,
      String permissionId) {
    reviews.flush();
    record(email, actor, row, type, before, reason, null, permissionId);
    return new Result(row, type);
  }

  private void record(
      String email,
      AuditActor actor,
      ReviewRecord row,
      Operation type,
      ReviewHistory.Before before,
      String reason,
      String related,
      String permissionId) {
    history.save(
        ReviewHistory.record(
            row, type, actor.id(), actor.name(), before, reason, related, permissionId));
    audit.record(
        email,
        row.getStoreId(),
        "REVIEW_" + type.name(),
        "REVIEW",
        row.getId(),
        null,
        null,
        before == null ? Map.of() : auditState(before, type),
        auditState(ReviewHistory.Before.of(row), type));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
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
