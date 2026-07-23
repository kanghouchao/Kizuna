package com.kizuna.shift.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ShiftRequestTest {

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
  void approve_fromPending_transitionsToApproved() {
    ShiftRequest request = pendingRequest();

    request.approve();

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);
  }

  @Test
  void decline_fromPending_transitionsToDeclined() {
    ShiftRequest request = pendingRequest();

    request.decline();

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
  }

  @Test
  void approve_whenAlreadyApproved_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.approve();

    assertThatThrownBy(request::approve)
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void approve_whenAlreadyDeclined_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.decline();

    assertThatThrownBy(request::approve)
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void decline_whenAlreadyApproved_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.approve();

    assertThatThrownBy(request::decline)
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void decline_whenAlreadyDeclined_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.decline();

    assertThatThrownBy(request::decline)
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }
}
