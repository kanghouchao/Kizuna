package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
import com.kizuna.review.application.ReviewActors;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.review.domain.ReviewDetailsView;
import com.kizuna.review.domain.ReviewHistoryRepository;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewPermissionRepository;
import com.kizuna.review.domain.ReviewRecord;
import com.kizuna.review.domain.ReviewRepository;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.FluentPageCaptor;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** 口コミの書き込みが受領を経由し、業務行のロックを受領の業務の中で取ること。受領の振る舞いは receipt モジュールの試験が持つ。 */
class ReviewWriteTest {
  private static final Long STORE_ID = 7L;
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private final ReviewRepository reviews = mock(ReviewRepository.class);
  private final ReviewOriginLookup origins = mock(ReviewOriginLookup.class);
  private final ReviewPermissionRepository permissions = mock(ReviewPermissionRepository.class);
  private final ReviewHistoryRepository history = mock(ReviewHistoryRepository.class);
  private final OperationReceipts operations = mock(OperationReceipts.class);
  private final ReviewActors actors = mock(ReviewActors.class);
  private final BusinessAudit audit = mock(BusinessAudit.class);
  private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
  private final ReviewService service =
      new ReviewService(
          reviews,
          origins,
          permissions,
          history,
          operations,
          actors,
          audit,
          Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC));
  private ReviewRecord row;

  @BeforeEach
  void setup() {
    when(actors.require(eq(STORE_ID), anyString(), any()))
        .thenReturn(new AuditActor(1L, "STAFF", "担当者"));
    row = ReviewRecord.receive(STORE_ID, input(null), 1L, "担当者", null, null);
    row.setId("10");
    row.setCreatedAt(AT);
    row.setUpdatedAt(AT);
    ReflectionTestUtils.setField(row, "version", 0L);
    when(reviews.findByIdAndStoreIdForUpdate("10", STORE_ID)).thenReturn(Optional.of(row));
    when(reviews.findProjectedByIdAndStoreId("10", STORE_ID))
        .thenAnswer(
            call -> Optional.of(projections.createProjection(ReviewDetailsView.class, row)));
    when(operations.once(any(), anyString(), any(), any()))
        .thenAnswer(
            call -> {
              Committed committed = call.<Supplier<Committed>>getArgument(2).get();
              return call.<Function<Receipt, Object>>getArgument(3)
                  .apply(
                      new Receipt(
                          "50",
                          committed.operation(),
                          committed.targetId(),
                          committed.facts(),
                          false));
            });
  }

  private static ReviewInput input(String origin) {
    return new ReviewInput("本文", null, ReceivedVia.PAPER, AT, origin);
  }

  private static ReviewCommand.Decide approve(String key) {
    return new ReviewCommand.Decide("10", 0L, Operation.APPROVED, "確認", key);
  }

  @Test
  void publicListEndsInIdAfterPublicationOrder() {
    var pages = new FluentPageCaptor();
    doAnswer(pages.answer())
        .when(reviews)
        .findBy(ArgumentMatchers.<Specification<ReviewRecord>>any(), any());

    service.listPublic(1L, PageRequest.of(2, 10, Sort.by("ignored")));

    var sort = pages.captured().getSort().toList();
    assertThat(sort)
        .first()
        .extracting(Sort.Order::getProperty)
        .isEqualTo("publication.displayOrder");
    assertThat(sort).last().isEqualTo(Sort.Order.asc("id"));
    assertThat(sort).extracting(Sort.Order::getProperty).doesNotContain("ignored");
    assertThat(pages.captured().getPageNumber()).isEqualTo(2);
  }

  @Test
  void firstWriteRecordsHistoryAuditAndTheReceiptOfTheCommittedVersion() {
    var result = service.write(STORE_ID, "actor", approve("approve"));

    verify(operations)
        .once(
            eq(new ReceiptKey(STORE_ID, 1L, "approve")),
            eq(approve("approve").fingerprint()),
            any(),
            any());
    verify(actors).require(STORE_ID, "actor", PermissionCode.REVIEW_MODERATE);
    verify(history).save(any());
    verify(audit)
        .record(
            "actor",
            STORE_ID,
            "REVIEW_APPROVED",
            "REVIEW",
            "10",
            null,
            null,
            Map.of("status", "PENDING", "permission_status", "NOT_GRANTED", "version", "0"),
            Map.of("status", "APPROVED", "permission_status", "NOT_GRANTED", "version", "0"));
    assertThat(result.operation().replayed()).isFalse();
    assertThat(result.operation().type()).isEqualTo(Operation.APPROVED);
    assertThat(result.operation().reviewId()).isEqualTo("10");
    assertThat(result.operation().committedVersion()).isZero();
    assertThat(result.review().status()).isEqualTo(Status.APPROVED);
  }

  @Test
  void staleVersionFailsWithoutRecordingAnOperation() {
    var stale = new ReviewCommand.Decide("10", 1L, Operation.APPROVED, "確認", "approve");

    assertThatThrownBy(() -> service.write(STORE_ID, "actor", stale))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(history, audit);
  }

  @Test
  void aReplayedKeyNeitherLocksTheReviewNorRevalidatesTheOrigin() {
    var receipt = new ReviewCommand.Receive(input("90"), "intake");
    doReturn(null).when(operations).once(any(), anyString(), any(), any());

    service.write(STORE_ID, "actor", approve("approve"));
    service.write(STORE_ID, "actor", receipt);

    verify(reviews, never()).findByIdAndStoreIdForUpdate(anyString(), anyLong());
    verifyNoInteractions(origins, history, audit);
  }

  @Test
  void replayStillRequiresTheOrderAuthorityOfALinkedIntake() {
    var receipt = new ReviewCommand.Receive(input("90"), "intake");
    doThrow(new AccessDeniedException("受注の権限なし"))
        .when(actors)
        .require(STORE_ID, "actor", PermissionCode.ORDER_MANAGE);

    assertThatThrownBy(() -> service.replayOnly(STORE_ID, "actor", receipt))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(operations);
  }
}
