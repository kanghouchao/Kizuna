package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.review.application.ReviewActors;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.review.domain.ReviewDetailsView;
import com.kizuna.review.domain.ReviewHistoryRepository;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewOperation;
import com.kizuna.review.domain.ReviewOperationRepository;
import com.kizuna.review.domain.ReviewPermissionRepository;
import com.kizuna.review.domain.ReviewRecord;
import com.kizuna.review.domain.ReviewRepository;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** 受領の照会順と再送の分岐。実データベースでの並行と一意制約は ReviewIntegrationTest が見る。 */
class ReviewWriteTest {
  private static final Long STORE_ID = 7L;
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private final ReviewRepository reviews = mock(ReviewRepository.class);
  private final ReviewOriginLookup origins = mock(ReviewOriginLookup.class);
  private final ReviewPermissionRepository permissions = mock(ReviewPermissionRepository.class);
  private final ReviewHistoryRepository history = mock(ReviewHistoryRepository.class);
  private final ReviewOperationRepository operations = mock(ReviewOperationRepository.class);
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
    when(operations.findByStoreIdAndActorIdAndDedupeKey(anyLong(), anyLong(), anyString()))
        .thenReturn(Optional.empty());
    when(operations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
  }

  private static ReviewInput input(String origin) {
    return new ReviewInput("本文", null, ReceivedVia.PAPER, AT, origin);
  }

  private static ReviewCommand.Decide approve(String key) {
    return new ReviewCommand.Decide("10", 0L, Operation.APPROVED, "確認", key);
  }

  private void stored(ReviewCommand command) {
    var operation =
        ReviewOperation.record(1L, command.key(), command.fingerprint(), Operation.APPROVED, row);
    operation.setId("50");
    operation.setCreatedAt(AT);
    when(operations.findByStoreIdAndActorIdAndDedupeKey(STORE_ID, 1L, command.key()))
        .thenReturn(Optional.of(operation));
  }

  @Test
  void firstWriteRecordsHistoryAuditAndOperationAfterLookingUpTheKeyUnderTheLock() {
    var result = service.write(STORE_ID, "actor", approve("approve"));

    var order = inOrder(reviews, operations);
    order.verify(reviews).findByIdAndStoreIdForUpdate("10", STORE_ID);
    order.verify(operations).findByStoreIdAndActorIdAndDedupeKey(STORE_ID, 1L, "approve");
    order.verify(operations).saveAndFlush(any());
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
    assertThat(result.review().status()).isEqualTo(Status.APPROVED);
  }

  @Test
  void storedKeyReplaysCurrentStateWithoutCheckingVersionOrWritingAgain() {
    var command = new ReviewCommand.Decide("10", 3L, Operation.APPROVED, "確認", "approve");
    stored(command);
    row.withdraw();

    var result = service.write(STORE_ID, "actor", command);

    assertThat(result.operation().replayed()).isTrue();
    assertThat(result.operation().id()).isEqualTo("50");
    assertThat(result.review().status()).isEqualTo(Status.WITHDRAWN);
    verify(operations, never()).saveAndFlush(any());
    verifyNoInteractions(history, audit);
  }

  @Test
  void storedKeyWithDifferentContentIsAConflict() {
    stored(approve("approve"));
    var changed = new ReviewCommand.Decide("10", 0L, Operation.REJECTED, "確認", "approve");

    assertThatThrownBy(() -> service.write(STORE_ID, "actor", changed))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.replayOnly(STORE_ID, "actor", changed))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void missingTargetFailsBeforeTheKeyIsLookedUp() {
    var missing = new ReviewCommand.Withdraw("99", 0L, "理由", "withdraw");

    assertThatThrownBy(() -> service.write(STORE_ID, "actor", missing))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(operations, history, audit);
  }

  @Test
  void staleVersionFailsWithoutRecordingAnOperation() {
    var stale = new ReviewCommand.Decide("10", 1L, Operation.APPROVED, "確認", "approve");

    assertThatThrownBy(() -> service.write(STORE_ID, "actor", stale))
        .isInstanceOf(ConflictException.class);
    verify(operations, never()).saveAndFlush(any());
    verifyNoInteractions(history, audit);
  }

  @Test
  void receiptReplayNeitherLocksNorRevalidatesTheOriginButStillRequiresOrderAuthority() {
    var receipt = new ReviewCommand.Receive(input("90"), "intake");
    stored(receipt);

    assertThat(service.write(STORE_ID, "actor", receipt).operation().replayed()).isTrue();
    verify(actors).require(STORE_ID, "actor", PermissionCode.ORDER_MANAGE);
    verify(reviews, never()).findByIdAndStoreIdForUpdate(anyString(), anyLong());
    verifyNoInteractions(origins);

    doThrow(new AccessDeniedException("受注の権限なし"))
        .when(actors)
        .require(STORE_ID, "actor", PermissionCode.ORDER_MANAGE);
    assertThatThrownBy(() -> service.replayOnly(STORE_ID, "actor", receipt))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void replayOnlyReturnsTheStoredOperationOrNotFound() {
    stored(approve("approve"));

    var replay = service.replayOnly(STORE_ID, "actor", approve("approve"));

    assertThat(replay.operation().replayed()).isTrue();
    assertThat(replay.operation().id()).isEqualTo("50");
    verify(reviews, never()).findByIdAndStoreIdForUpdate(anyString(), anyLong());
    assertThatThrownBy(() -> service.replayOnly(STORE_ID, "actor", approve("missing")))
        .isInstanceOf(NotFoundException.class);
  }
}
