package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.application.CastService;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.api.dto.AttendanceCancellationRequest;
import com.kizuna.shift.api.dto.AttendanceCorrectionRequest;
import com.kizuna.shift.api.dto.AttendanceCreateRequest;
import com.kizuna.shift.api.dto.AttendanceMapper;
import com.kizuna.shift.api.dto.AttendanceResponse;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceCorrection;
import com.kizuna.shift.domain.AttendanceCorrectionRepository;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

  private static final String ACTOR_EMAIL = "manager@kizuna.test";
  private static final Long ACTOR_ID = 42L;
  private static final String CAST_ID = "cast-1";
  private static final String SHIFT_ID = "shift-1";

  /** シフトの勤務日。飛び込みの判定が返す営業日とは別の値にして、どちらを継承したのかを断言で区別できるようにする。 */
  private static final LocalDate SHIFT_WORK_DATE = LocalDate.of(2026, 8, 18);

  private static final LocalDate DERIVED_BUSINESS_DATE = LocalDate.of(2026, 8, 17);
  private static final LocalDateTime START = LocalDateTime.of(2026, 8, 19, 1, 30);

  @Mock private AttendanceRepository attendanceRepository;
  @Mock private AttendanceCorrectionRepository correctionRepository;
  @Mock private AttendanceMapper attendanceMapper;
  @Mock private ShiftRepository shiftRepository;
  @Mock private CastService castService;
  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private BusinessDateService businessDateService;

  @InjectMocks private AttendanceService attendanceService;

  private void givenActor() {
    PlatformUser actor =
        PlatformUser.builder()
            .email(ACTOR_EMAIL)
            .password("encoded")
            .displayName("店長")
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .roleIds(Set.of(1L))
            .build();
    actor.setId(ACTOR_ID);
    when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(actor));
  }

  private Shift shift(String castId) {
    return shift(castId, ShiftStatus.CONFIRMED);
  }

  private Shift shift(String castId, ShiftStatus status) {
    return Shift.builder()
        .castId(castId)
        .workDate(SHIFT_WORK_DATE)
        .startTime(LocalTime.of(19, 0))
        .endTime(LocalTime.of(2, 0))
        .status(status)
        .published(true)
        .build();
  }

  private AttendanceCreateRequest createRequest(String shiftId) {
    AttendanceCreateRequest request = new AttendanceCreateRequest();
    request.setCastId(CAST_ID);
    request.setShiftId(shiftId);
    request.setActualStartAt(START);
    return request;
  }

  private AttendanceCorrectionRequest correctionRequest(LocalDate businessDate) {
    AttendanceCorrectionRequest request = new AttendanceCorrectionRequest();
    request.setBusinessDate(businessDate);
    request.setActualStartAt(START);
    request.setActualEndAt(START.plusHours(4));
    request.setWaitingPlace("2番待機室");
    return request;
  }

  private AttendanceCancellationRequest cancellationRequest(String reason) {
    AttendanceCancellationRequest request = new AttendanceCancellationRequest();
    request.setReason(reason);
    return request;
  }

  private Attendance saved() {
    ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
    verify(attendanceRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("シフト紐づきの実績が営業日をシフトの勤務日から継承すること")
  void shiftLinkedAttendanceInheritsWorkDate() {
    givenActor();
    when(castService.existsForCurrentStoreForUpdate(CAST_ID)).thenReturn(true);
    when(shiftRepository.findScopedByIdForUpdate(SHIFT_ID)).thenReturn(Optional.of(shift(CAST_ID)));
    when(attendanceRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

    attendanceService.record(createRequest(SHIFT_ID), ACTOR_EMAIL);

    // 実開始 01:30 は暦日でも判定でも 8/19 側だが、シフトの勤務日 8/18 が勝つ。
    assertThat(saved().getBusinessDate()).isEqualTo(SHIFT_WORK_DATE);
    verify(businessDateService, never()).businessDateOf(any());
  }

  @Test
  @DisplayName("飛び込み（シフトなし）の営業日が実開始時刻から自動判定されること")
  void walkInAttendanceDerivesBusinessDateFromActualStart() {
    givenActor();
    when(castService.existsForCurrentStoreForUpdate(CAST_ID)).thenReturn(true);
    when(businessDateService.businessDateOf(START)).thenReturn(DERIVED_BUSINESS_DATE);
    when(attendanceRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

    attendanceService.record(createRequest(null), ACTOR_EMAIL);

    Attendance attendance = saved();
    assertThat(attendance.getBusinessDate()).isEqualTo(DERIVED_BUSINESS_DATE);
    assertThat(attendance.getShiftId()).isNull();
    assertThat(attendance.getCreatedBy()).isEqualTo(ACTOR_ID);
  }

  @Test
  @DisplayName("他人のシフトへ紐づけた記録を拒否すること")
  void rejectsAttendanceLinkedToAnotherCastsShift() {
    givenActor();
    when(castService.existsForCurrentStoreForUpdate(CAST_ID)).thenReturn(true);
    when(shiftRepository.findScopedByIdForUpdate(SHIFT_ID))
        .thenReturn(Optional.of(shift("cast-2")));

    assertThatThrownBy(() -> attendanceService.record(createRequest(SHIFT_ID), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("キャスト");
  }

  @Test
  @DisplayName("下書き（TENTATIVE）のシフトへ紐づけた記録を拒否すること")
  void rejectsAttendanceLinkedToATentativeShift() {
    givenActor();
    when(castService.existsForCurrentStoreForUpdate(CAST_ID)).thenReturn(true);
    when(shiftRepository.findScopedByIdForUpdate(SHIFT_ID))
        .thenReturn(Optional.of(shift(CAST_ID, ShiftStatus.TENTATIVE)));

    assertThatThrownBy(() -> attendanceService.record(createRequest(SHIFT_ID), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("確定");
  }

  @Test
  @DisplayName("現店舗に居ないキャストの記録が 404 になること")
  void rejectsAttendanceForUnknownCast() {
    givenActor();
    when(castService.existsForCurrentStoreForUpdate(CAST_ID)).thenReturn(false);

    assertThatThrownBy(() -> attendanceService.record(createRequest(null), ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("未取消の一意性違反が 409 へ写り、当たった索引で文言が分かれること")
  void translatesActiveUniquenessViolations() {
    givenActor();
    when(castService.existsForCurrentStoreForUpdate(CAST_ID)).thenReturn(true);
    when(businessDateService.businessDateOf(START)).thenReturn(DERIVED_BUSINESS_DATE);
    when(attendanceRepository.saveAndFlush(any()))
        .thenThrow(uniqueViolation("uq_t_attendances_active_cast_business_date"))
        .thenThrow(uniqueViolation("uq_t_attendances_active_shift"));

    assertThatThrownBy(() -> attendanceService.record(createRequest(null), ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("営業日");
    assertThatThrownBy(() -> attendanceService.record(createRequest(null), ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("シフト");
  }

  @Test
  @DisplayName("訂正が編集前のスナップショットを残してから就地更新すること")
  void correctionPersistsThePreEditSnapshot() {
    givenActor();
    Attendance attendance =
        Attendance.record(CAST_ID, null, DERIVED_BUSINESS_DATE, START, null, "1番待機室", ACTOR_ID);
    when(attendanceRepository.findById("att-1")).thenReturn(Optional.of(attendance));
    when(attendanceRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

    attendanceService.correct("att-1", correctionRequest(SHIFT_WORK_DATE), ACTOR_EMAIL);

    ArgumentCaptor<AttendanceCorrection> captor =
        ArgumentCaptor.forClass(AttendanceCorrection.class);
    verify(correctionRepository).save(captor.capture());
    AttendanceCorrection snapshot = captor.getValue();
    assertThat(snapshot.getBusinessDate()).isEqualTo(DERIVED_BUSINESS_DATE);
    assertThat(snapshot.getActualEndAt()).isNull();
    assertThat(snapshot.getWaitingPlace()).isEqualTo("1番待機室");
    assertThat(snapshot.getCorrectedBy()).isEqualTo(ACTOR_ID);
    assertThat(snapshot.getCorrectedAt()).isNotNull();

    assertThat(attendance.getBusinessDate()).isEqualTo(SHIFT_WORK_DATE);
    assertThat(attendance.getActualEndAt()).isEqualTo(START.plusHours(4));
  }

  @Test
  @DisplayName("シフト紐づきの実績の営業日をシフトの勤務日からずらす訂正を拒否すること")
  void correctionCannotDetachBusinessDateFromTheShift() {
    givenActor();
    Attendance attendance =
        Attendance.record(CAST_ID, SHIFT_ID, SHIFT_WORK_DATE, START, null, null, ACTOR_ID);
    when(attendanceRepository.findById("att-1")).thenReturn(Optional.of(attendance));
    when(shiftRepository.findScopedByIdForUpdate(SHIFT_ID)).thenReturn(Optional.of(shift(CAST_ID)));

    assertThatThrownBy(
            () ->
                attendanceService.correct(
                    "att-1", correctionRequest(SHIFT_WORK_DATE.minusDays(1)), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("営業日");
    verify(correctionRepository, never()).save(any());
  }

  @Test
  @DisplayName("取消が標記だけを付け、行を消さないこと")
  void cancellationMarksTheRow() {
    givenActor();
    Attendance attendance =
        Attendance.record(CAST_ID, null, DERIVED_BUSINESS_DATE, START, null, null, ACTOR_ID);
    when(attendanceRepository.findById("att-1")).thenReturn(Optional.of(attendance));

    attendanceService.cancel("att-1", cancellationRequest("誤って記録したため"), ACTOR_EMAIL);

    assertThat(attendance.isCancelled()).isTrue();
    assertThat(attendance.getCancelledBy()).isEqualTo(ACTOR_ID);
    assertThat(attendance.getCancelledReason()).isEqualTo("誤って記録したため");
    verify(attendanceRepository).save(attendance);
  }

  @Test
  @DisplayName("照会がキャスト指定の有無で読み口を切り替え、どちらも取消済みを除外すること")
  void lookupSwitchesOnCastAndAlwaysExcludesCancelled() {
    Attendance attendance =
        Attendance.record(CAST_ID, null, DERIVED_BUSINESS_DATE, START, null, null, ACTOR_ID);
    when(attendanceRepository.findByBusinessDateAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(
            DERIVED_BUSINESS_DATE))
        .thenReturn(List.of(attendance));
    when(attendanceRepository
            .findByBusinessDateAndCastIdAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(
                DERIVED_BUSINESS_DATE, CAST_ID))
        .thenReturn(List.of());
    when(attendanceMapper.toResponse(attendance)).thenReturn(new AttendanceResponse());

    assertThat(attendanceService.list(DERIVED_BUSINESS_DATE, null)).hasSize(1);
    assertThat(attendanceService.list(DERIVED_BUSINESS_DATE, CAST_ID)).isEmpty();
  }

  private static DataIntegrityViolationException uniqueViolation(String constraintName) {
    return new DataIntegrityViolationException(
        "duplicate key",
        new ConstraintViolationException(
            "duplicate key",
            null,
            ConstraintViolationException.ConstraintKind.UNIQUE,
            constraintName));
  }
}
