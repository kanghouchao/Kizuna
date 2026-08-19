package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shift.api.dto.ShiftDetailResponse;
import com.kizuna.shift.api.dto.ShiftRequestLineageResponse;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestStatus;
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiftLineageServiceTest {

  private static final String SHIFT_ID = "shift-1";
  private static final String CAST_ID = "cast-1";
  private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 18);
  private static final Long APPROVER_ID = 42L;
  private static final Long RECORDER_ID = 43L;

  @Mock private ShiftRepository shiftRepository;
  @Mock private ShiftRequestRepository shiftRequestRepository;
  @Mock private AttendanceRepository attendanceRepository;
  @Mock private PlatformUserRepository platformUserRepository;

  @InjectMocks private ShiftLineageService shiftLineageService;

  private static Shift confirmedShift() {
    Shift shift =
        Shift.builder()
            .castId(CAST_ID)
            .workDate(WORK_DATE)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .published(true)
            .createdBy(APPROVER_ID)
            .build();
    shift.setId(SHIFT_ID);
    return shift;
  }

  private static ShiftRequest request(ShiftRequestType type, ShiftRequestStatus status) {
    ShiftRequest request =
        ShiftRequest.builder()
            .castId(CAST_ID)
            .workDate(WORK_DATE)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .type(type)
            .status(status)
            .shiftId(SHIFT_ID)
            .processedBy(status == ShiftRequestStatus.PENDING ? null : APPROVER_ID)
            .processedAt(
                status == ShiftRequestStatus.PENDING
                    ? null
                    : OffsetDateTime.parse("2026-08-17T10:00:00+09:00"))
            .build();
    request.setId("request-" + type);
    return request;
  }

  private static Attendance attendance() {
    Attendance attendance =
        Attendance.record(
            CAST_ID,
            SHIFT_ID,
            WORK_DATE,
            LocalDateTime.of(2026, 8, 18, 18, 5),
            null,
            "1番待機室",
            RECORDER_ID);
    attendance.setId("attendance-1");
    return attendance;
  }

  private static PlatformUser user(Long id, String displayName) {
    PlatformUser user =
        PlatformUser.builder()
            .email(id + "@kizuna.test")
            .password("encoded")
            .displayName(displayName)
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .roleIds(Set.of(1L))
            .build();
    user.setId(id);
    return user;
  }

  private void givenShiftExists() {
    when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(confirmedShift()));
  }

  @Test
  @DisplayName("申請（NEW / CHANGE）と実績が実行主体・日時付きで辿れる")
  void assemblesTheWholeLineageWithActors() {
    givenShiftExists();
    when(shiftRequestRepository.findByShiftIdOrderByCreatedAtAscIdAsc(SHIFT_ID))
        .thenReturn(
            List.of(
                request(ShiftRequestType.NEW, ShiftRequestStatus.APPROVED),
                request(ShiftRequestType.CHANGE, ShiftRequestStatus.APPROVED)));
    when(attendanceRepository.findByShiftIdAndCancelledAtIsNull(SHIFT_ID))
        .thenReturn(Optional.of(attendance()));
    when(platformUserRepository.findAllById(any()))
        .thenReturn(List.of(user(APPROVER_ID, "承認した店長"), user(RECORDER_ID, "記録した店員")));

    ShiftDetailResponse detail = shiftLineageService.detail(SHIFT_ID);

    assertThat(detail.getCreatedBy().getId()).isEqualTo(APPROVER_ID);
    assertThat(detail.getCreatedBy().getName()).as("実行主体は id だけでなく名前まで解決すること").isEqualTo("承認した店長");
    assertThat(detail.getUpdatedBy()).as("書き換えの無い行では最終更新者を出さないこと").isNull();

    assertThat(detail.getRequests())
        .extracting(ShiftRequestLineageResponse::getType)
        .as("出生（NEW）と変更申請（CHANGE）の両方が背骨で辿れること")
        .containsExactly("NEW", "CHANGE");
    assertThat(detail.getRequests())
        .allSatisfy(
            request -> {
              assertThat(request.getCastId()).as("申請の実行主体はキャスト本人").isEqualTo(CAST_ID);
              assertThat(request.getProcessedBy().getName()).isEqualTo("承認した店長");
              assertThat(request.getProcessedAt()).isNotNull();
            });

    ArgumentCaptor<Iterable<Long>> requestedIds = ArgumentCaptor.captor();
    verify(platformUserRepository).findAllById(requestedIds.capture());
    assertThat(requestedIds.getValue())
        .as("実行主体は系列全体で一度に引くこと")
        .containsExactlyInAnyOrder(APPROVER_ID, RECORDER_ID);

    assertThat(detail.getAttendance().getCreatedBy().getName()).isEqualTo("記録した店員");
    assertThat(detail.getAttendance().getWaitingPlace()).isEqualTo("1番待機室");
    assertThat(detail.getAttendance().getBusinessDate()).isEqualTo(WORK_DATE);
  }

  @Test
  @DisplayName("取消済みしか無いシフトの実績欄は空になる")
  void cancelledAttendanceIsNotPartOfTheLineage() {
    givenShiftExists();
    when(shiftRequestRepository.findByShiftIdOrderByCreatedAtAscIdAsc(SHIFT_ID))
        .thenReturn(List.of());
    when(attendanceRepository.findByShiftIdAndCancelledAtIsNull(SHIFT_ID))
        .thenReturn(Optional.empty());

    ShiftDetailResponse detail = shiftLineageService.detail(SHIFT_ID);

    assertThat(detail.getAttendance()).isNull();
    assertThat(detail.getRequests()).as("店舗が直接作成したシフトでは申請が無いこと").isEmpty();
  }

  @Test
  @DisplayName("実行主体が一人も居なければ利用者を引きに行かない")
  void skipsTheActorLookupWhenNoActorIsRecorded() {
    Shift actorless =
        Shift.builder()
            .castId(CAST_ID)
            .workDate(WORK_DATE)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .build();
    actorless.setId(SHIFT_ID);
    when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(actorless));
    when(shiftRequestRepository.findByShiftIdOrderByCreatedAtAscIdAsc(SHIFT_ID))
        .thenReturn(List.of());
    when(attendanceRepository.findByShiftIdAndCancelledAtIsNull(SHIFT_ID))
        .thenReturn(Optional.empty());

    ShiftDetailResponse detail = shiftLineageService.detail(SHIFT_ID);

    assertThat(detail.getCreatedBy()).isNull();
    verify(platformUserRepository, never()).findAllById(any());
  }

  @Test
  @DisplayName("作用域の外のシフトは見つからない")
  void unknownShiftIsNotFound() {
    lenient().when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftLineageService.detail(SHIFT_ID))
        .isInstanceOf(NotFoundException.class);
  }
}
