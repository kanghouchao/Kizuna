package com.kizuna.shift.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ShiftRequestTest {

  private static final Long ACTOR = 7L;
  private static final OffsetDateTime PROCESSED_AT =
      OffsetDateTime.parse("2026-08-18T10:00:00+09:00");

  private ShiftRequest pendingRequest() {
    return ShiftRequest.builder()
        .castId("c1")
        .workDate(LocalDate.of(2999, 8, 1))
        .startTime(LocalTime.of(18, 0))
        .endTime(LocalTime.of(23, 0))
        .build();
  }

  @Test
  void builder_defaultsStatusToPending() {
    assertThat(pendingRequest().getStatus()).isEqualTo(ShiftRequestStatus.PENDING);
  }

  @Test
  void builder_defaultsTypeToNewWithoutShiftId() {
    ShiftRequest request = pendingRequest();

    assertThat(request.getType()).isEqualTo(ShiftRequestType.NEW);
    assertThat(request.getShiftId()).isNull();
  }

  @Test
  void builder_changeType_carriesTargetShiftIdAndSameTransitions() {
    ShiftRequest request =
        ShiftRequest.builder()
            .castId("c1")
            .type(ShiftRequestType.CHANGE)
            .shiftId("sh1")
            .workDate(LocalDate.of(2999, 8, 2))
            .startTime(LocalTime.of(19, 0))
            .endTime(LocalTime.of(22, 0))
            .build();

    assertThat(request.getType()).isEqualTo(ShiftRequestType.CHANGE);
    assertThat(request.getShiftId()).isEqualTo("sh1");

    // 変更申請も同じ状態系列（PENDING→APPROVED/DECLINED）に従う
    request.approve(ACTOR, PROCESSED_AT);
    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);
    assertThatThrownBy(() -> request.decline(ACTOR, PROCESSED_AT))
        .isInstanceOf(ShiftRequestStateException.class);
  }

  @Test
  void approve_fromPending_transitionsToApproved() {
    ShiftRequest request = pendingRequest();

    request.approve(ACTOR, PROCESSED_AT);

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);
  }

  @Test
  void decline_fromPending_transitionsToDeclined() {
    ShiftRequest request = pendingRequest();

    request.decline(ACTOR, PROCESSED_AT);

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
  }

  @Test
  void approve_stampsProcessorAndTime() {
    ShiftRequest request = pendingRequest();

    request.approve(ACTOR, PROCESSED_AT);

    assertThat(request.getProcessedBy()).isEqualTo(ACTOR);
    assertThat(request.getProcessedAt()).isEqualTo(PROCESSED_AT);
  }

  @Test
  void decline_stampsProcessorAndTime() {
    ShiftRequest request = pendingRequest();

    request.decline(ACTOR, PROCESSED_AT);

    assertThat(request.getProcessedBy()).isEqualTo(ACTOR);
    assertThat(request.getProcessedAt()).isEqualTo(PROCESSED_AT);
  }

  @Test
  void linkShift_bindsGeneratedShiftToTheRequest() {
    ShiftRequest request = pendingRequest();

    request.linkShift("sh-generated");

    assertThat(request.getShiftId()).isEqualTo("sh-generated");
  }

  @Test
  void approve_whenAlreadyApproved_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.approve(ACTOR, PROCESSED_AT);

    assertThatThrownBy(() -> request.approve(ACTOR, PROCESSED_AT))
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void approve_whenAlreadyDeclined_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.decline(ACTOR, PROCESSED_AT);

    assertThatThrownBy(() -> request.approve(ACTOR, PROCESSED_AT))
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void decline_whenAlreadyApproved_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.approve(ACTOR, PROCESSED_AT);

    assertThatThrownBy(() -> request.decline(ACTOR, PROCESSED_AT))
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void decline_whenAlreadyDeclined_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.decline(ACTOR, PROCESSED_AT);

    assertThatThrownBy(() -> request.decline(ACTOR, PROCESSED_AT))
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }
}
