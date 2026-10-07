package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.order.revieworigin.ReviewOriginFacts;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.review.application.ReviewActors;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewCorrection;
import com.kizuna.review.domain.ReviewDetailsView;
import com.kizuna.review.domain.ReviewHistoryRepository;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewOperation;
import com.kizuna.review.domain.ReviewOperationRepository;
import com.kizuna.review.domain.ReviewPermission;
import com.kizuna.review.domain.ReviewPermissionRepository;
import com.kizuna.review.domain.ReviewPermissionView;
import com.kizuna.review.domain.ReviewRecord;
import com.kizuna.review.domain.ReviewRepository;
import com.kizuna.review.domain.ReviewSearch;
import com.kizuna.review.domain.ReviewSummaryView;
import com.kizuna.review.domain.ReviewValues.Basis;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewApplicationTest {
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private final ReviewRepository rows = mock(ReviewRepository.class);
  private final ReviewPermissionRepository permissions = mock(ReviewPermissionRepository.class);
  private final ReviewOperationRepository operations = mock(ReviewOperationRepository.class);
  private final ReviewHistoryRepository history = mock(ReviewHistoryRepository.class);
  private final ReviewActors actors = mock(ReviewActors.class);
  private final ReviewOriginLookup origins = mock(ReviewOriginLookup.class);
  private final BusinessAudit audit = mock(BusinessAudit.class);
  private final Map<String, ReviewRecord> records = new HashMap<>();
  private final Map<String, ReviewPermission> consent = new HashMap<>();
  private final Map<String, ReviewOperation> receipts = new HashMap<>();
  private final AtomicLong ids = new AtomicLong(100);
  private ReviewService service;
  private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();

  @BeforeEach
  void setup() {
    service =
        new ReviewService(
            rows,
            origins,
            permissions,
            history,
            operations,
            actors,
            audit,
            Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC));
    when(actors.require(anyString(), any())).thenReturn(new AuditActor(1L, "STAFF", "担当者"));
    when(rows.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ReviewRecord row = call.getArgument(0);
              metadata(row);
              records.put(row.getId(), row);
              return row;
            });
    when(rows.findProjectedById(anyString()))
        .thenAnswer(
            call ->
                Optional.ofNullable(records.get(call.getArgument(0)))
                    .map(r -> projections.createProjection(ReviewDetailsView.class, r)));
    when(rows.lockById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(records.get(call.getArgument(0))));
    when(permissions.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ReviewPermission permission = call.getArgument(0);
              metadata(permission);
              consent.put(permission.getReviewId(), permission);
              return permission;
            });
    when(permissions.findProjectedByReviewId(anyString()))
        .thenAnswer(
            call ->
                Optional.ofNullable(consent.get(call.getArgument(0)))
                    .map(r -> projections.createProjection(ReviewPermissionView.class, r)));
    when(permissions.findByReviewId(anyString()))
        .thenAnswer(call -> Optional.ofNullable(consent.get(call.getArgument(0))));
    when(operations.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ReviewOperation operation = call.getArgument(0);
              metadata(operation);
              receipts.put(operation.getDedupeKey(), operation);
              return operation;
            });
    when(operations.findByActorIdAndDedupeKey(anyLong(), anyString()))
        .thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(1))));
  }

  private void metadata(StoreScopedEntity entity) {
    entity.setId(Long.toString(ids.incrementAndGet()));
    entity.setStoreId(1L);
    entity.setCreatedAt(AT);
    entity.setUpdatedAt(AT);
    ReflectionTestUtils.setField(entity, "version", 0L);
  }

  private ReviewInput input() {
    return new ReviewInput("  原文\r\n二行目  ", "  表示名  ", ReceivedVia.PAPER, AT, null);
  }

  private String create() {
    return service.create("actor", input(), "create").review().id();
  }

  @Test
  void originalReceiptReturnsCurrentStateAndCurrentAuthorizationAlwaysApplies() {
    String id = create();
    var approval = new ReviewChange(id, 0L, Operation.APPROVED, "確認理由", "approve");
    service.change("actor", approval);
    service.change("actor", new ReviewChange(id, 0L, Operation.WITHDRAWN, "取り下げ理由", "withdraw"));
    var replay = service.replayCreate("actor", input(), "create");
    assertThat(replay.review().status()).isEqualTo(Status.WITHDRAWN);
    assertThat(replay.review().body()).isEqualTo("  原文\n二行目  ");
    assertThat(service.replayChange("actor", approval).operation().replayed()).isTrue();
    assertThat(service.change("actor", approval).review().status()).isEqualTo(Status.WITHDRAWN);
    assertThatThrownBy(
            () ->
                service.create(
                    "actor", new ReviewInput("別本文", null, ReceivedVia.PAPER, AT, null), "create"))
        .isInstanceOf(ConflictException.class);
    doThrow(new AccessDeniedException("権限なし"))
        .when(actors)
        .require("actor", PermissionCode.REVIEW_MANAGE);
    assertThatThrownBy(() -> service.create("actor", input(), "create"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void permissionRevocationRetainsEvidenceAndDoesNotReverseApprovalOrWithdrawal() {
    String id = create();
    service.change("actor", new ReviewChange(id, 0L, Operation.APPROVED, "審査理由", "approve"));
    var grant =
        new ReviewChange(
            id,
            0L,
            Operation.PERMISSION_GRANTED,
            null,
            "grant",
            Basis.ELECTRONIC_RECORD,
            AT,
            "  根拠  ");
    var permitted = service.change("actor", grant);
    assertThat(permitted.review().publicationEligible()).isTrue();
    service.change("actor", new ReviewChange(id, 0L, Operation.WITHDRAWN, "取り下げ", "withdraw"));
    service.change(
        "actor",
        new ReviewChange(id, 0L, Operation.PERMISSION_REVOKED, "撤回理由", "revoke", null, AT, null));
    var replay = service.change("actor", grant);
    assertThat(replay.review().publicationBlockers())
        .containsExactly("WITHDRAWN", "PERMISSION_REVOKED");
    assertThat(replay.review().permission().evidenceNote()).isEqualTo("根拠");
    assertThat(replay.review().permission().revocation().reason()).isEqualTo("撤回理由");
    assertThatThrownBy(
            () ->
                service.change(
                    "actor",
                    new ReviewChange(
                        id,
                        0L,
                        Operation.PERMISSION_REVOKED,
                        "再撤回",
                        "revoke-again",
                        null,
                        AT,
                        null)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void correctionStartsIndependentRecordAndReplaysItsCurrentDetail() {
    String id = create();
    var correction = new ReviewCorrection(id, 0L, "訂正", input(), "correct");
    var result = service.correct("actor", correction);
    assertThat(result.review().supersedesId()).isEqualTo(id);
    assertThat(result.review().permissionStatus()).isEqualTo(PermissionStatus.NOT_GRANTED);
    assertThat(service.detail("actor", id).supersededById()).isEqualTo(result.review().id());
    service.change(
        "actor", new ReviewChange(result.review().id(), 0L, Operation.REJECTED, "却下理由", "reject"));
    assertThat(service.correct("actor", correction).review().status()).isEqualTo(Status.REJECTED);
    assertThat(service.replayCorrection("actor", correction).operation().id())
        .isEqualTo(result.operation().id());
    assertThatThrownBy(
            () -> service.correct("actor", new ReviewCorrection(id, 0L, "再訂正", input(), "second")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void originIsOnlyValidatedForNewReceiptsButItsAuthorityIsRequiredForReplay() {
    var input = new ReviewInput("本文", null, ReceivedVia.VERBAL, AT, "90");
    when(origins.find("90")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create("actor", input, "origin"))
        .isInstanceOf(NotFoundException.class);
    when(origins.find("90"))
        .thenReturn(Optional.of(new ReviewOriginFacts("90", 4L, "CONFIRMED", false)));
    assertThatThrownBy(() -> service.create("actor", input, "origin"))
        .isInstanceOf(ServiceException.class);
    when(origins.find("90"))
        .thenReturn(Optional.of(new ReviewOriginFacts("90", 4L, "COMPLETED", false)));
    var result = service.create("actor", input, "origin");
    assertThat(result.review().originOrderVersion()).isEqualTo(4);
    when(origins.find("90"))
        .thenReturn(Optional.of(new ReviewOriginFacts("90", 5L, "COMPLETED", true)));
    assertThat(service.create("actor", input, "origin").operation().replayed()).isTrue();
    assertThatThrownBy(() -> service.create("actor", input, "other"))
        .isInstanceOf(ServiceException.class);
    doThrow(new AccessDeniedException("受注の権限なし"))
        .when(actors)
        .require("actor", PermissionCode.ORDER_MANAGE);
    assertThatThrownBy(() -> service.replayCreate("actor", input, "origin"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void missingAndMalformedOperationsFailWithoutSuccessfulReceipts() {
    String id = create();
    assertThatThrownBy(() -> service.detail("actor", "999")).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.replayCreate("actor", input(), "missing"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.replayChange(
                    "actor", new ReviewChange(id, 0L, Operation.APPROVED, "確認", "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.replayCorrection(
                    "actor", new ReviewCorrection(id, 0L, "訂正", input(), "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.change(
                    "actor", new ReviewChange("999", 0L, Operation.APPROVED, "確認", "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.correct("actor", new ReviewCorrection("999", 0L, "訂正", input(), "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.change(
                    "actor", new ReviewChange(id, 1L, Operation.APPROVED, "確認", "old-version")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                service.change("actor", new ReviewChange(id, 0L, Operation.RECEIVED, "不正", "bad")))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void listAndHistoryExposeOnlyTheirPublicResponseFields() {
    String id = create();
    when(rows.findBy(any(Specification.class), any(Function.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(projections.createProjection(ReviewSummaryView.class, records.get(id)))));
    var page =
        service.list(
            "actor",
            new ReviewSearch(0, 20, null, null, null, null, ReviewSearch.Order.RECEIVED_DESC));
    assertThat(page.getContent().getFirst().displayName()).isEqualTo("表示名");
    when(history.findBy(any(Specification.class), any(Function.class)))
        .thenReturn(new PageImpl<>(List.of()));
    assertThat(service.history("actor", id, null, 20).content()).isEmpty();
    assertThatThrownBy(() -> service.history("actor", id, null, 101))
        .isInstanceOf(ServiceException.class);
    assertThat(service.history("actor", id, "MTIz", 1).content()).isEmpty();
  }
}
