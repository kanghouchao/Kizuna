package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.order.revieworigin.ReviewOriginFacts;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.review.application.ReviewActors;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewCorrection;
import com.kizuna.review.domain.ReviewDetailsView;
import com.kizuna.review.domain.ReviewHistory;
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
import com.kizuna.shared.publication.Publication;
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
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor.SpecificationFluentQuery;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewApplicationTest {
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private static final Long STORE_ID = 7L;
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
    when(actors.require(eq(STORE_ID), anyString(), any()))
        .thenReturn(new AuditActor(1L, "STAFF", "担当者"));
    when(rows.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ReviewRecord row = call.getArgument(0);
              metadata(row);
              records.put(row.getId(), row);
              return row;
            });
    when(rows.findProjectedByIdAndStoreId(anyString(), anyLong()))
        .thenAnswer(
            call ->
                scoped(records, call.getArgument(0), call.getArgument(1))
                    .map(r -> projections.createProjection(ReviewDetailsView.class, r)));
    when(rows.findByIdAndStoreIdForUpdate(anyString(), anyLong()))
        .thenAnswer(call -> scoped(records, call.getArgument(0), call.getArgument(1)));
    when(permissions.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ReviewPermission permission = call.getArgument(0);
              metadata(permission);
              consent.put(permission.getReviewId(), permission);
              return permission;
            });
    when(permissions.findProjectedByStoreIdAndReviewId(anyLong(), anyString()))
        .thenAnswer(
            call ->
                scoped(consent, call.getArgument(1), call.getArgument(0))
                    .map(r -> projections.createProjection(ReviewPermissionView.class, r)));
    when(permissions.findByStoreIdAndReviewId(anyLong(), anyString()))
        .thenAnswer(call -> scoped(consent, call.getArgument(1), call.getArgument(0)));
    when(operations.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ReviewOperation operation = call.getArgument(0);
              metadata(operation);
              receipts.put(operation.getDedupeKey(), operation);
              return operation;
            });
    when(operations.findByStoreIdAndActorIdAndDedupeKey(anyLong(), anyLong(), anyString()))
        .thenAnswer(call -> scoped(receipts, call.getArgument(2), call.getArgument(0)));
    when(history.save(any()))
        .thenAnswer(
            call -> {
              ReviewHistory row = call.getArgument(0);
              assertThat(row.getStoreId()).isEqualTo(STORE_ID);
              return row;
            });
  }

  private <T extends StoreScopedEntity> Optional<T> scoped(
      Map<String, T> rows, String key, Long storeId) {
    return Optional.ofNullable(rows.get(key)).filter(row -> row.getStoreId().equals(storeId));
  }

  /** 保存される行は、生成した側が店舗を設定していなければならない。 */
  private void metadata(StoreScopedEntity entity) {
    assertThat(entity.getStoreId()).isEqualTo(STORE_ID);
    entity.setId(Long.toString(ids.incrementAndGet()));
    entity.setCreatedAt(AT);
    entity.setUpdatedAt(AT);
    ReflectionTestUtils.setField(entity, "version", 0L);
  }

  private ReviewInput input() {
    return new ReviewInput("  原文\r\n二行目  ", "  表示名  ", ReceivedVia.PAPER, AT, null);
  }

  private String create() {
    return service.create(STORE_ID, "actor", input(), "create").review().id();
  }

  @Test
  void originalReceiptReturnsCurrentStateAndCurrentAuthorizationAlwaysApplies() {
    String id = create();
    var approval = new ReviewChange(id, 0L, Operation.APPROVED, "確認理由", "approve");
    service.change(STORE_ID, "actor", approval);
    service.change(
        STORE_ID, "actor", new ReviewChange(id, 0L, Operation.WITHDRAWN, "取り下げ理由", "withdraw"));
    var replay = service.replayCreate(STORE_ID, "actor", input(), "create");
    assertThat(replay.review().status()).isEqualTo(Status.WITHDRAWN);
    assertThat(replay.review().body()).isEqualTo("  原文\n二行目  ");
    assertThat(service.replayChange(STORE_ID, "actor", approval).operation().replayed()).isTrue();
    assertThat(service.change(STORE_ID, "actor", approval).review().status())
        .isEqualTo(Status.WITHDRAWN);
    assertThatThrownBy(
            () ->
                service.create(
                    STORE_ID,
                    "actor",
                    new ReviewInput("別本文", null, ReceivedVia.PAPER, AT, null),
                    "create"))
        .isInstanceOf(ConflictException.class);
    doThrow(new AccessDeniedException("権限なし"))
        .when(actors)
        .require(STORE_ID, "actor", PermissionCode.REVIEW_MANAGE);
    assertThatThrownBy(() -> service.create(STORE_ID, "actor", input(), "create"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void moderatorPublicationIsDisplayedOnlyOnceApprovedAndPermitted() {
    String id = create();
    var publish = ReviewChange.publication(id, 0L, new Publication(true, AT, null, 1), "publish");
    var hidden = service.change(STORE_ID, "actor", publish);
    verify(actors).require(STORE_ID, "actor", PermissionCode.REVIEW_MODERATE);
    assertThat(hidden.review().publication().published()).isTrue();
    assertThat(hidden.review().displayed()).isFalse();
    service.change(
        STORE_ID, "actor", new ReviewChange(id, 0L, Operation.APPROVED, "審査理由", "approve"));
    var shown =
        service.change(
            STORE_ID,
            "actor",
            new ReviewChange(
                id, 0L, Operation.PERMISSION_GRANTED, null, "grant", Basis.WRITTEN, AT, "根拠"));
    assertThat(shown.review().displayed()).isTrue();
    assertThat(service.change(STORE_ID, "actor", publish).operation().replayed()).isTrue();
    assertThatThrownBy(
            () ->
                service.change(
                    STORE_ID,
                    "actor",
                    ReviewChange.publication(id, 0L, Publication.HIDDEN, "publish")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> ReviewChange.publication(id, 0L, null, "missing"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void permissionRevocationRetainsEvidenceAndDoesNotReverseApprovalOrWithdrawal() {
    String id = create();
    service.change(
        STORE_ID, "actor", new ReviewChange(id, 0L, Operation.APPROVED, "審査理由", "approve"));
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
    var permitted = service.change(STORE_ID, "actor", grant);
    assertThat(permitted.review().publicationEligible()).isTrue();
    service.change(
        STORE_ID, "actor", new ReviewChange(id, 0L, Operation.WITHDRAWN, "取り下げ", "withdraw"));
    service.change(
        STORE_ID,
        "actor",
        new ReviewChange(id, 0L, Operation.PERMISSION_REVOKED, "撤回理由", "revoke", null, AT, null));
    var replay = service.change(STORE_ID, "actor", grant);
    assertThat(replay.review().publicationBlockers())
        .containsExactly("WITHDRAWN", "PERMISSION_REVOKED");
    assertThat(replay.review().permission().evidenceNote()).isEqualTo("根拠");
    assertThat(replay.review().permission().revocation().reason()).isEqualTo("撤回理由");
    assertThatThrownBy(
            () ->
                service.change(
                    STORE_ID,
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
    var result = service.correct(STORE_ID, "actor", correction);
    verify(audit)
        .record(
            eq("actor"),
            eq(STORE_ID),
            eq("REVIEW_CORRECTION_LINKED"),
            eq("REVIEW"),
            eq(id),
            eq(null),
            eq(null),
            any(),
            eq(Map.of("status", "WITHDRAWN", "permission_status", "NOT_GRANTED", "version", "0")));
    assertThat(result.review().supersedesId()).isEqualTo(id);
    assertThat(result.review().permissionStatus()).isEqualTo(PermissionStatus.NOT_GRANTED);
    assertThat(service.detail(STORE_ID, "actor", id).supersededById())
        .isEqualTo(result.review().id());
    service.change(
        STORE_ID,
        "actor",
        new ReviewChange(result.review().id(), 0L, Operation.REJECTED, "却下理由", "reject"));
    assertThat(service.correct(STORE_ID, "actor", correction).review().status())
        .isEqualTo(Status.REJECTED);
    assertThat(service.replayCorrection(STORE_ID, "actor", correction).operation().id())
        .isEqualTo(result.operation().id());
    assertThatThrownBy(
            () ->
                service.correct(
                    STORE_ID, "actor", new ReviewCorrection(id, 0L, "再訂正", input(), "second")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void originIsOnlyValidatedForNewReceiptsButItsAuthorityIsRequiredForReplay() {
    var input = new ReviewInput("本文", null, ReceivedVia.VERBAL, AT, "90");
    when(origins.find(STORE_ID, "90")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create(STORE_ID, "actor", input, "origin"))
        .isInstanceOf(NotFoundException.class);
    when(origins.find(STORE_ID, "90"))
        .thenReturn(Optional.of(new ReviewOriginFacts("90", 4L, "CONFIRMED", false)));
    assertThatThrownBy(() -> service.create(STORE_ID, "actor", input, "origin"))
        .isInstanceOf(ServiceException.class);
    when(origins.find(STORE_ID, "90"))
        .thenReturn(Optional.of(new ReviewOriginFacts("90", 4L, "COMPLETED", false)));
    var result = service.create(STORE_ID, "actor", input, "origin");
    assertThat(result.review().originOrderVersion()).isEqualTo(4);
    when(origins.find(STORE_ID, "90"))
        .thenReturn(Optional.of(new ReviewOriginFacts("90", 5L, "COMPLETED", true)));
    assertThat(service.create(STORE_ID, "actor", input, "origin").operation().replayed()).isTrue();
    assertThatThrownBy(() -> service.create(STORE_ID, "actor", input, "other"))
        .isInstanceOf(ServiceException.class);
    doThrow(new AccessDeniedException("受注の権限なし"))
        .when(actors)
        .require(STORE_ID, "actor", PermissionCode.ORDER_MANAGE);
    assertThatThrownBy(() -> service.replayCreate(STORE_ID, "actor", input, "origin"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void missingAndMalformedOperationsFailWithoutSuccessfulReceipts() {
    String id = create();
    assertThatThrownBy(() -> service.detail(STORE_ID, "actor", "999"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.replayCreate(STORE_ID, "actor", input(), "missing"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.replayChange(
                    STORE_ID,
                    "actor",
                    new ReviewChange(id, 0L, Operation.APPROVED, "確認", "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.replayCorrection(
                    STORE_ID, "actor", new ReviewCorrection(id, 0L, "訂正", input(), "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.change(
                    STORE_ID,
                    "actor",
                    new ReviewChange("999", 0L, Operation.APPROVED, "確認", "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.correct(
                    STORE_ID, "actor", new ReviewCorrection("999", 0L, "訂正", input(), "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.change(
                    STORE_ID,
                    "actor",
                    new ReviewChange(id, 1L, Operation.APPROVED, "確認", "old-version")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                service.change(
                    STORE_ID, "actor", new ReviewChange(id, 0L, Operation.RECEIVED, "不正", "bad")))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void listAndHistoryExposeOnlyTheirPublicResponseFields() {
    String id = create();
    when(rows.findBy(
            ArgumentMatchers.<Specification<ReviewRecord>>any(),
            ArgumentMatchers
                .<Function<? super SpecificationFluentQuery<ReviewRecord>, Object>>any()))
        .thenReturn(
            new PageImpl<>(
                List.of(projections.createProjection(ReviewSummaryView.class, records.get(id)))));
    var page =
        service.list(
            STORE_ID,
            "actor",
            new ReviewSearch(
                PageRequest.of(0, 20), null, null, null, null, ReviewSearch.Order.RECEIVED_DESC));
    assertThat(page.getContent().getFirst().displayName()).isEqualTo("表示名");
    when(history.findBy(
            ArgumentMatchers.<Specification<ReviewHistory>>any(),
            ArgumentMatchers
                .<Function<? super SpecificationFluentQuery<ReviewHistory>, Object>>any()))
        .thenReturn(new PageImpl<>(List.of()));
    assertThat(service.history(STORE_ID, "actor", id, PageRequest.of(0, 20)).getContent())
        .isEmpty();
  }
}
