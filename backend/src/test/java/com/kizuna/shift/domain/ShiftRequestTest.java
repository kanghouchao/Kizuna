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

  private ShiftRequest pendingChangeRequest() {
    return ShiftRequest.builder()
        .castId("c1")
        .kind(ShiftRequestKind.CHANGE)
        .targetShiftId("s1")
        .workDate(LocalDate.of(2999, 8, 2))
        .startTime(LocalTime.of(19, 0))
        .endTime(LocalTime.of(22, 0))
        .build();
  }

  @Test
  void builder_defaultsStatusToPending() {
    assertThat(pendingRequest().getStatus()).isEqualTo(ShiftRequestStatus.PENDING);
  }

  @Test
  void builder_defaultsKindToNew() {
    assertThat(pendingRequest().getKind()).isEqualTo(ShiftRequestKind.NEW);
  }

  @Test
  void toShiftPatch_carriesOnlyRequestedDateAndTimes() {
    ShiftPatch patch = pendingChangeRequest().toShiftPatch();

    assertThat(patch.workDate()).isEqualTo(LocalDate.of(2999, 8, 2));
    assertThat(patch.startTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(patch.endTime()).isEqualTo(LocalTime.of(22, 0));
    // 申請が持たない属性は「変更しない」に固定される。承認が担当キャストや確定状態を巻き込まないことの担保。
    assertThat(patch.castId()).isNull();
    assertThat(patch.status()).isNull();
    assertThat(patch.publicVisible()).isNull();
  }

  @Test
  void toShiftPatch_leavesUnrelatedShiftAttributesUntouched() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.of(2999, 8, 1))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status("CONFIRMED")
            .publicVisible(false)
            .build();

    shift.apply(pendingChangeRequest().toShiftPatch());

    assertThat(shift.getWorkDate()).isEqualTo(LocalDate.of(2999, 8, 2));
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(22, 0));
    assertThat(shift.getCastId()).isEqualTo("c1");
    assertThat(shift.getStatus()).isEqualTo("CONFIRMED");
    assertThat(shift.isPublicVisible()).isFalse();
  }

  @Test
  void toShiftPatch_onNewRequest_throwsStateException() {
    assertThatThrownBy(pendingRequest()::toShiftPatch)
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("変更申請");
  }

  @Test
  void approve_fromPending_transitionsToApproved() {
    ShiftRequest request = pendingRequest();

    request.approve("staff@kizuna.test");

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);
    assertThat(request.getDecidedBy()).isEqualTo("staff@kizuna.test");
    assertThat(request.getDecidedAt()).isNotNull();
  }

  @Test
  void decline_fromPending_transitionsToDeclined() {
    ShiftRequest request = pendingRequest();

    request.decline("staff@kizuna.test");

    assertThat(request.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
    assertThat(request.getDecidedBy()).isEqualTo("staff@kizuna.test");
    assertThat(request.getDecidedAt()).isNotNull();
  }

  @Test
  void approve_whenAlreadyApproved_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.approve("staff@kizuna.test");

    assertThatThrownBy(() -> request.approve("staff@kizuna.test"))
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void approve_whenAlreadyDeclined_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.decline("staff@kizuna.test");

    assertThatThrownBy(() -> request.approve("staff@kizuna.test"))
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void decline_whenAlreadyApproved_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.approve("staff@kizuna.test");

    assertThatThrownBy(() -> request.decline("staff@kizuna.test"))
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void decline_whenAlreadyDeclined_throwsStateException() {
    ShiftRequest request = pendingRequest();
    request.decline("staff@kizuna.test");

    assertThatThrownBy(() -> request.decline("staff@kizuna.test"))
        .isInstanceOf(ShiftRequestStateException.class)
        .hasMessageContaining("処理済み");
  }

  @Test
  void linkToShift_connectsApprovedNewRequestToTheShiftSourceOfTruth() {
    ShiftRequest request = pendingRequest();

    request.linkToShift("s1");

    assertThat(request.getTargetShiftId()).isEqualTo("s1");
  }
}
