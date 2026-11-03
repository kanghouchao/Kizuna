package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shift.api.dto.ShiftChangeRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestCreateRequest;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.CastShiftRequestView;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CastShiftRequestServiceTest {
  @Mock private StoreRepository storeRepository;

  @Mock private UserRepository userRepository;
  @Mock private CastEnrollmentRepository castRepository;
  @Mock private ShiftRequestRepository shiftRequestRepository;
  @Mock private ShiftRepository shiftRepository;
  @Mock private AttendanceRepository attendanceRepository;
  @Mock private BusinessDateService businessDateService;

  @Mock private BusinessAudit audit;

  @InjectMocks private CastShiftRequestService service;

  @BeforeEach
  void membership() {
    lenient()
        .when(castRepository.findByIdAndStoreIdForUpdate(any(), any()))
        .thenReturn(Optional.of(CastEnrollment.builder().build()));
  }

  private static final String EMAIL = "cast@kizuna.test";

  private ShiftRequestCreateRequest validRequest() {
    ShiftRequestCreateRequest req = new ShiftRequestCreateRequest();
    req.setStoreId(1L);
    req.setWorkDate(LocalDate.of(2999, 8, 1));
    req.setStartTime(LocalTime.of(18, 0));
    req.setEndTime(LocalTime.of(23, 0));
    return req;
  }

  private User userWithId(long id) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    return user;
  }

  @Test
  void submit_rejectsWhenStartEqualsEnd() {
    ShiftRequestCreateRequest req = validRequest();
    req.setStartTime(LocalTime.of(20, 0));
    req.setEndTime(LocalTime.of(20, 0));

    assertThatThrownBy(() -> service.submit(EMAIL, req.command()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("開始時刻と終了時刻");

    verifyNoInteractions(userRepository, castRepository, shiftRequestRepository);
  }

  @Test
  void submit_rejectsWhenWorkDateBeforeToday() {
    ShiftRequestCreateRequest req = validRequest();
    req.setWorkDate(LocalDate.of(2000, 1, 1));
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));

    assertThatThrownBy(() -> service.submit(EMAIL, req.command()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("本日以降");

    verifyNoInteractions(userRepository, castRepository, shiftRequestRepository);
  }

  @Test
  void submit_throwsWhenEmailUnknown() {
    ShiftRequestCreateRequest req = validRequest();
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(EMAIL, req.command()))
        .isInstanceOf(StaleSessionException.class)
        .hasMessageContaining("認証セッションの主体が存在しません");

    verifyNoInteractions(castRepository, shiftRequestRepository);
  }

  @Test
  void submit_rejectsWhenNotAffiliatedWithStore() {
    ShiftRequestCreateRequest req = validRequest();
    User user = userWithId(42L);
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByUserIdAndStoreId(42L, 1L)).thenReturn(List.of());

    assertThatThrownBy(() -> service.submit(EMAIL, req.command()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("所属していません");

    verifyNoInteractions(shiftRequestRepository);
  }

  @Test
  void submit_savesWithResolvedCastIdAndStoreId() {
    ShiftRequestCreateRequest req = validRequest();
    User user = userWithId(42L);
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByUserIdAndStoreId(42L, 1L)).thenReturn(List.of("cast-1"));
    when(shiftRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    ShiftRequest result = service.submit(EMAIL, req.command());

    ArgumentCaptor<ShiftRequest> captor = ArgumentCaptor.forClass(ShiftRequest.class);
    verify(shiftRequestRepository).save(captor.capture());
    ShiftRequest saved = captor.getValue();
    assertThat(result).isSameAs(saved);
    assertThat(saved.getCastId()).isEqualTo("cast-1");
    assertThat(saved.getStoreId()).isEqualTo(1L);
    assertThat(saved.getWorkDate()).isEqualTo(req.getWorkDate());
    assertThat(saved.getStartTime()).isEqualTo(req.getStartTime());
    assertThat(saved.getEndTime()).isEqualTo(req.getEndTime());
  }

  private ShiftChangeRequestCreateRequest validChangeRequest() {
    ShiftChangeRequestCreateRequest req = new ShiftChangeRequestCreateRequest();
    req.setShiftId("sh1");
    req.setWorkDate(LocalDate.of(2999, 8, 2));
    req.setStartTime(LocalTime.of(19, 0));
    req.setEndTime(LocalTime.of(23, 0));
    return req;
  }

  private Shift shiftOf(String castId, ShiftStatus status) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(LocalDate.of(2999, 8, 1))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(status)
            .build();
    shift.setId("sh1");
    shift.setStoreId(7L);
    return shift;
  }

  @Test
  void submitChange_rejectsWhenStartEqualsEnd() {
    ShiftChangeRequestCreateRequest req = validChangeRequest();
    req.setStartTime(LocalTime.of(20, 0));
    req.setEndTime(LocalTime.of(20, 0));

    assertThatThrownBy(() -> service.submitChange(EMAIL, req.command()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("開始時刻と終了時刻");

    verifyNoInteractions(userRepository, shiftRepository, shiftRequestRepository);
  }

  @Test
  void submitChange_rejectsWhenWorkDateBeforeToday() {
    ShiftChangeRequestCreateRequest req = validChangeRequest();
    req.setWorkDate(LocalDate.of(2000, 1, 1));
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));

    assertThatThrownBy(() -> service.submitChange(EMAIL, req.command()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("本日以降");

    verifyNoInteractions(userRepository, shiftRepository, shiftRequestRepository);
  }

  @Test
  void submitChange_throwsWhenShiftMissing() {
    ShiftChangeRequestCreateRequest req = validChangeRequest();
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    User user = userWithId(42L);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByUserId(42L)).thenReturn(List.of("cast-1"));
    when(shiftRepository.findByIdAndCastIdIn("sh1", List.of("cast-1")))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submitChange(EMAIL, req.command()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("対象のシフトが見つかりません");

    verifyNoInteractions(shiftRequestRepository);
  }

  @Test
  void submitChange_throwsWhenShiftBelongsToAnotherCast() {
    ShiftChangeRequestCreateRequest req = validChangeRequest();
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    User user = userWithId(42L);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByUserId(42L)).thenReturn(List.of("cast-1"));
    // 他人のシフトは本人の cast_id 集合で引く問い合わせに現れない。
    when(shiftRepository.findByIdAndCastIdIn("sh1", List.of("cast-1")))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submitChange(EMAIL, req.command()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("対象のシフトが見つかりません");

    verifyNoInteractions(shiftRequestRepository);
  }

  @Test
  void submitChange_rejectsWhenShiftHasActiveAttendance() {
    ShiftChangeRequestCreateRequest req = validChangeRequest();
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    User user = userWithId(42L);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByUserId(42L)).thenReturn(List.of("cast-1"));
    when(shiftRepository.findByIdAndCastIdIn("sh1", List.of("cast-1")))
        .thenReturn(Optional.of(shiftOf("cast-1", ShiftStatus.CONFIRMED)));
    when(attendanceRepository.hasActiveAttendance(7L, "sh1")).thenReturn(true);

    assertThatThrownBy(() -> service.submitChange(EMAIL, req.command()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("実績が記録されているシフト");

    verifyNoInteractions(shiftRequestRepository);
  }

  @Test
  void submitChange_rejectsWhenShiftNotConfirmed() {
    ShiftChangeRequestCreateRequest req = validChangeRequest();
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    User user = userWithId(42L);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByUserId(42L)).thenReturn(List.of("cast-1"));
    when(shiftRepository.findByIdAndCastIdIn("sh1", List.of("cast-1")))
        .thenReturn(Optional.of(shiftOf("cast-1", ShiftStatus.TENTATIVE)));

    assertThatThrownBy(() -> service.submitChange(EMAIL, req.command()))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("確定済みのシフトのみ");

    verifyNoInteractions(shiftRequestRepository);
  }

  @Test
  void submitChange_savesChangeTypedRequestDerivedFromShift() {
    ShiftChangeRequestCreateRequest req = validChangeRequest();
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    User user = userWithId(42L);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByUserId(42L)).thenReturn(List.of("cast-1"));
    when(shiftRepository.findByIdAndCastIdIn("sh1", List.of("cast-1")))
        .thenReturn(Optional.of(shiftOf("cast-1", ShiftStatus.CONFIRMED)));
    when(shiftRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    ShiftRequest result = service.submitChange(EMAIL, req.command());

    ArgumentCaptor<ShiftRequest> captor = ArgumentCaptor.forClass(ShiftRequest.class);
    verify(shiftRequestRepository).save(captor.capture());
    ShiftRequest saved = captor.getValue();
    assertThat(result).isSameAs(saved);
    assertThat(saved.getType()).isEqualTo(ShiftRequestType.CHANGE);
    assertThat(saved.getShiftId()).isEqualTo("sh1");
    assertThat(saved.getCastId()).isEqualTo("cast-1");
    assertThat(saved.getOriginalWorkDate())
        .as("申請時点の対象シフトの時間帯が控えられること")
        .isEqualTo(LocalDate.of(2999, 8, 1));
    assertThat(saved.getOriginalStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(saved.getOriginalEndTime()).isEqualTo(LocalTime.of(23, 0));
    assertThat(saved.getStoreId()).as("店舗はシフトから導出されること").isEqualTo(7L);
    assertThat(saved.getWorkDate()).isEqualTo(req.getWorkDate());
    assertThat(saved.getStartTime()).isEqualTo(req.getStartTime());
    assertThat(saved.getEndTime()).isEqualTo(req.getEndTime());
  }

  @Test
  void history_returnsEmptyWhenNoCastLinked() {
    User user = userWithId(42L);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByUserId(42L)).thenReturn(List.of());

    Page<CastShiftRequestView> result = service.history(EMAIL, PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    verifyNoInteractions(shiftRequestRepository);
  }

  @Test
  void history_mapsAcrossCastIds() {
    User user = userWithId(42L);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    List<String> castIds = List.of("cast-store-a", "cast-store-b");
    when(castRepository.findIdsByUserId(42L)).thenReturn(castIds);

    CastShiftRequestView view = mock(CastShiftRequestView.class);
    when(shiftRequestRepository.findHistoryByCastIds(eq(castIds), any()))
        .thenReturn(new PageImpl<>(List.of(view)));

    Page<CastShiftRequestView> result = service.history(EMAIL, PageRequest.of(0, 20));

    assertThat(result.getContent()).containsExactly(view);
    verify(shiftRequestRepository, never()).findByStoreIdOrderByCreatedAtAsc(any());
  }
}
