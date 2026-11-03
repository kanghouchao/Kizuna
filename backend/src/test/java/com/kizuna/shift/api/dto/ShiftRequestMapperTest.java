package com.kizuna.shift.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shift.application.ShiftViews.InboxRequest;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShiftRequestMapperTest {

  private final ShiftRequestMapper mapper = new ShiftRequestMapperImpl();

  private static ShiftRequest change() {
    ShiftRequest request =
        ShiftRequest.builder()
            .castId("cast-1")
            .type(ShiftRequestType.CHANGE)
            .shiftId("sh1")
            .workDate(LocalDate.of(2999, 8, 2))
            .startTime(LocalTime.of(19, 0))
            .endTime(LocalTime.of(23, 0))
            .build();
    request.setId("sr1");
    return request;
  }

  @Test
  @DisplayName("inbox の変更申請は対象シフトの現行日時と承認可否を内联すること")
  void inboxInlinesTheTargetSlot() {
    Shift target =
        Shift.builder()
            .castId("cast-1")
            .workDate(LocalDate.of(2999, 8, 1))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(22, 0))
            .status(ShiftStatus.CONFIRMED)
            .build();

    StoreShiftRequestResponse response =
        mapper.toStoreResponse(new InboxRequest(change(), target, true));

    assertThat(response.getId()).isEqualTo("sr1");
    assertThat(response.getWorkDate()).isEqualTo(LocalDate.of(2999, 8, 2));
    assertThat(response.getCurrentWorkDate()).isEqualTo(LocalDate.of(2999, 8, 1));
    assertThat(response.getCurrentStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(response.getCurrentEndTime()).isEqualTo(LocalTime.of(22, 0));
    assertThat(response.getApprovable()).isTrue();
  }

  @Test
  @DisplayName("対象シフトの無い inbox 行は現行日時を空のまま、承認可否だけを載せること")
  void inboxWithoutTargetLeavesTheSlotEmpty() {
    StoreShiftRequestResponse response =
        mapper.toStoreResponse(new InboxRequest(change(), null, false));

    assertThat(response.getCurrentWorkDate()).isNull();
    assertThat(response.getCurrentStartTime()).isNull();
    assertThat(response.getCurrentEndTime()).isNull();
    assertThat(response.getApprovable()).isFalse();
  }
}
