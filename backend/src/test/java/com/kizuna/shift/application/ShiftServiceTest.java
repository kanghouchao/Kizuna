package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.api.dto.ShiftResponse;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftPatch;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

  @Mock private ShiftRepository shiftRepository;
  @Mock private AttendanceRepository attendanceRepository;
  @Mock private ShiftMapper shiftMapper;
  @Mock private CastService castService;
  @Mock private CastRepository castRepository;
  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private BusinessDateService businessDateService;

  @InjectMocks private ShiftService shiftService;

  private static final String ACTOR_EMAIL = "manager@kizuna.test";
  private static final Long ACTOR_ID = 42L;

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

  private ShiftCreateRequest validCreateRequest() {
    ShiftCreateRequest req = new ShiftCreateRequest();
    req.setCastId("c1");
    req.setWorkDate(LocalDate.of(2026, 7, 8));
    req.setStartTime(LocalTime.of(18, 0));
    req.setEndTime(LocalTime.of(23, 0));
    return req;
  }

  @Test
  void list_returnsShiftsInRange() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    Shift s = Shift.builder().castId("c1").build();

    when(shiftRepository.findByWorkDateBetween(from, to)).thenReturn(List.of(s));
    ShiftResponse resp = new ShiftResponse();
    resp.setId("s1");
    when(shiftMapper.toResponse(s)).thenReturn(resp);

    List<ShiftResponse> result = shiftService.list(from, to);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo("s1");
  }

  @Test
  void create_savesAndReturns() {
    ShiftCreateRequest req = validCreateRequest();

    Shift entity = Shift.builder().castId("c1").status(ShiftStatus.TENTATIVE).build();

    givenActor();
    when(castService.existsForCurrentStore("c1")).thenReturn(true);
    when(shiftMapper.toEntity(req, ACTOR_ID)).thenReturn(entity);
    when(shiftRepository.save(any()))
        .thenAnswer(
            i -> {
              Shift s = i.getArgument(0);
              s.setId("s_new");
              return s;
            });

    ShiftResponse resp = new ShiftResponse();
    resp.setId("s_new");
    when(shiftMapper.toResponse(any())).thenReturn(resp);

    ShiftResponse res = shiftService.create(req, ACTOR_EMAIL);
    assertThat(res.getId()).isEqualTo("s_new");
    // 作成の実行者は認証主体から解決して写像へ渡す（実際に列へ載ることは ShiftCrossStoreIT が見る）
    verify(shiftMapper).toEntity(req, ACTOR_ID);
  }

  @Test
  void create_rejectsWhenEndEqualsStart() {
    ShiftCreateRequest req = validCreateRequest();
    req.setStartTime(LocalTime.of(20, 0));
    req.setEndTime(LocalTime.of(20, 0));

    assertThatThrownBy(() -> shiftService.create(req, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("開始時刻と終了時刻");
  }

  @Test
  void create_rejectsWhenCastNotInStore() {
    ShiftCreateRequest req = validCreateRequest();

    when(castService.existsForCurrentStore("c1")).thenReturn(false);

    assertThatThrownBy(() -> shiftService.create(req, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("キャストが見つかりません");
  }

  @Test
  void update_appliesPatchAndSaves() {
    Shift s = Shift.builder().castId("c1").status(ShiftStatus.TENTATIVE).build();
    s.setId("s1");

    givenActor();
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    when(shiftRepository.save(any())).thenReturn(s);

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setStatus(ShiftStatus.CONFIRMED);
    when(shiftMapper.toPatch(req))
        .thenReturn(new ShiftPatch(null, null, null, null, ShiftStatus.CONFIRMED));

    ShiftResponse resp = new ShiftResponse();
    resp.setStatus("CONFIRMED");
    when(shiftMapper.toResponse(s)).thenReturn(resp);

    ShiftResponse result = shiftService.update("s1", req, ACTOR_EMAIL);
    assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    assertThat(s.getStatus()).isEqualTo(ShiftStatus.CONFIRMED);
    assertThat(s.getUpdatedBy()).as("直接編集は updated_by に実行者を印字すること").isEqualTo(ACTOR_ID);
  }

  @Test
  void update_throwsWhenNotFound() {
    when(shiftRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftService.update("missing", new ShiftUpdateRequest(), ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("シフトが見つかりません");
  }

  @Test
  void update_rejectsWhenCastNotInStore() {
    Shift s = Shift.builder().castId("c1").build();
    s.setId("s1");
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    when(castService.existsForCurrentStore("foreign")).thenReturn(false);

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setCastId("foreign");

    assertThatThrownBy(() -> shiftService.update("s1", req, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("キャストが見つかりません");
  }

  @Test
  void update_rejectsWhenEndEqualsStart() {
    Shift s = Shift.builder().build();
    s.setId("s1");
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setStartTime(LocalTime.of(20, 0));
    req.setEndTime(LocalTime.of(20, 0));

    assertThatThrownBy(() -> shiftService.update("s1", req, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("開始時刻と終了時刻");
  }

  @Test
  void update_rejectsWhenPartialUpdateMergesToEqualTimes() {
    // 既存 18:00-22:00 に start だけ 22:00 → 既存 end 22:00 とマージで一致 → 拒否
    Shift s = Shift.builder().startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(22, 0)).build();
    s.setId("s1");
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setStartTime(LocalTime.of(22, 0));

    assertThatThrownBy(() -> shiftService.update("s1", req, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("開始時刻と終了時刻");
  }

  @Test
  void update_rejectsWorkDateChangeWhenActiveAttendanceExists() {
    Shift s = shiftWithAttribution();
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    when(attendanceRepository.hasActiveAttendance("s1")).thenReturn(true);

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setWorkDate(LocalDate.of(2026, 7, 9));

    assertThatThrownBy(() -> shiftService.update("s1", req, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("勤務日とキャストは変更できません");
    verify(shiftRepository, never()).save(any());
  }

  @Test
  void update_rejectsCastChangeWhenActiveAttendanceExists() {
    Shift s = shiftWithAttribution();
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    when(castService.existsForCurrentStore("c2")).thenReturn(true);
    when(attendanceRepository.hasActiveAttendance("s1")).thenReturn(true);

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setCastId("c2");

    assertThatThrownBy(() -> shiftService.update("s1", req, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("勤務日とキャストは変更できません");
    verify(shiftRepository, never()).save(any());
  }

  @Test
  void update_allowsTimeAndStatusChangeWhenActiveAttendanceExists() {
    // 実績の有無と無関係に通す面。禁改が時刻・status まで巻き込んでいれば赤になる。
    // 実績「あり」を敷いたうえで通ることが主張なので、短絡で呼ばれない stub は lenient で置く。
    Shift s = shiftWithAttribution();
    givenActor();
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    lenient().when(attendanceRepository.hasActiveAttendance("s1")).thenReturn(true);
    when(shiftRepository.save(any())).thenReturn(s);

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setStartTime(LocalTime.of(19, 0));
    req.setStatus(ShiftStatus.TENTATIVE);
    when(shiftMapper.toPatch(req))
        .thenReturn(new ShiftPatch(null, null, LocalTime.of(19, 0), null, ShiftStatus.TENTATIVE));
    when(shiftMapper.toResponse(s)).thenReturn(new ShiftResponse());

    shiftService.update("s1", req, ACTOR_EMAIL);

    assertThat(s.getStartTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(s.getStatus()).isEqualTo(ShiftStatus.TENTATIVE);
  }

  @Test
  void update_allowsResendingTheSameAttributionWhenActiveAttendanceExists() {
    // 同値の再送は変更ではない。全項目を送り返す客体を禁改が誤って撥ねないこと。
    Shift s = shiftWithAttribution();
    givenActor();
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    when(castService.existsForCurrentStore("c1")).thenReturn(true);
    lenient().when(attendanceRepository.hasActiveAttendance("s1")).thenReturn(true);
    when(shiftRepository.save(any())).thenReturn(s);

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setCastId("c1");
    req.setWorkDate(LocalDate.of(2026, 7, 8));
    when(shiftMapper.toPatch(req))
        .thenReturn(new ShiftPatch("c1", LocalDate.of(2026, 7, 8), null, null, null));
    when(shiftMapper.toResponse(s)).thenReturn(new ShiftResponse());

    shiftService.update("s1", req, ACTOR_EMAIL);

    verify(shiftRepository).save(s);
  }

  @Test
  void update_allowsAttributionChangeWhenAttendanceWasCancelled() {
    // 取消後は変更できる。逃げ道（取消 → 変更 → 再記録）が塞がっていないこと。
    Shift s = shiftWithAttribution();
    givenActor();
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    when(attendanceRepository.hasActiveAttendance("s1")).thenReturn(false);
    when(shiftRepository.save(any())).thenReturn(s);

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setWorkDate(LocalDate.of(2026, 7, 9));
    when(shiftMapper.toPatch(req))
        .thenReturn(new ShiftPatch(null, LocalDate.of(2026, 7, 9), null, null, null));
    when(shiftMapper.toResponse(s)).thenReturn(new ShiftResponse());

    shiftService.update("s1", req, ACTOR_EMAIL);

    assertThat(s.getWorkDate()).isEqualTo(LocalDate.of(2026, 7, 9));
  }

  @Test
  void delete_removes() {
    when(shiftRepository.existsById("s1")).thenReturn(true);
    shiftService.delete("s1");
    verify(shiftRepository).deleteById("s1");
  }

  @Test
  void delete_rejectsWhenAnyAttendanceReferencesTheShift() {
    when(shiftRepository.existsById("s1")).thenReturn(true);
    when(attendanceRepository.existsByShiftId("s1")).thenReturn(true);

    assertThatThrownBy(() -> shiftService.delete("s1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("実績が記録されているシフトは削除できません");
    verify(shiftRepository, never()).deleteById("s1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(shiftRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> shiftService.delete("missing"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("シフトが見つかりません");
  }

  /** 実績が物化する帰属（勤務日・キャスト）を持つ既存シフト。禁改の主題はこの 2 欄だけである。 */
  private static Shift shiftWithAttribution() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.of(2026, 7, 8))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .build();
    shift.setId("s1");
    return shift;
  }

  private Cast activeCast(String id, String name, String photoUrl) {
    Cast cast = Cast.builder().name(name).photoUrl(photoUrl).build();
    cast.setId(id);
    return cast;
  }

  @Test
  void changePublication_flipsFlagAndStampsActor() {
    Shift shift = Shift.builder().castId("c1").status(ShiftStatus.CONFIRMED).build();
    givenActor();
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));
    when(shiftRepository.save(shift)).thenReturn(shift);
    when(shiftMapper.toResponse(shift)).thenReturn(new ShiftResponse());

    shiftService.changePublication("s1", false, ACTOR_EMAIL);

    assertThat(shift.isPublished()).isFalse();
    assertThat(shift.getUpdatedBy()).isEqualTo(ACTOR_ID);
  }

  @Test
  void changePublication_leavesStatusAndSlotUntouched() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.of(2026, 7, 8))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .build();
    givenActor();
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));
    when(shiftRepository.save(shift)).thenReturn(shift);
    when(shiftMapper.toResponse(shift)).thenReturn(new ShiftResponse());

    shiftService.changePublication("s1", false, ACTOR_EMAIL);

    assertThat(shift.getStatus()).as("公開可否は状態機械の一部ではない").isEqualTo(ShiftStatus.CONFIRMED);
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(23, 0));
  }

  @Test
  void changePublication_rejectsUnknownShift() {
    when(shiftRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftService.changePublication("missing", false, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void listPublicToday_joinsCastInfoAndPreservesRepoOrder() {
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    Shift first =
        Shift.builder()
            .castId("cA")
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(20, 0))
            .build();
    Shift second =
        Shift.builder()
            .castId("cB")
            .startTime(LocalTime.of(21, 0))
            .endTime(LocalTime.of(23, 0))
            .build();
    when(shiftRepository.findByWorkDateAndStatusAndPublishedTrueOrderByStartTimeAsc(any(), any()))
        .thenReturn(List.of(first, second));
    when(castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE"))
        .thenReturn(List.of(activeCast("cA", "キャストA", "urlA"), activeCast("cB", "キャストB", "urlB")));

    List<PublicShiftResponse> result = shiftService.listPublicToday();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getCastId()).isEqualTo("cA");
    assertThat(result.get(0).getCastName()).isEqualTo("キャストA");
    assertThat(result.get(0).getCastPhotoUrl()).isEqualTo("urlA");
    assertThat(result.get(0).getStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(result.get(0).getEndTime()).isEqualTo(LocalTime.of(20, 0));
    assertThat(result.get(1).getCastId()).isEqualTo("cB");
    assertThat(result.get(1).getCastName()).isEqualTo("キャストB");
    assertThat(result.get(1).getCastPhotoUrl()).isEqualTo("urlB");
  }

  @Test
  void listPublicToday_queriesTodayInConfiguredTimezoneWithPublicationGate() {
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    LocalDate expectedToday = LocalDate.now(ZoneId.of("Asia/Tokyo"));
    when(shiftRepository.findByWorkDateAndStatusAndPublishedTrueOrderByStartTimeAsc(any(), any()))
        .thenReturn(List.of());

    shiftService.listPublicToday();

    ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<ShiftStatus> statusCaptor = ArgumentCaptor.forClass(ShiftStatus.class);
    verify(shiftRepository)
        .findByWorkDateAndStatusAndPublishedTrueOrderByStartTimeAsc(
            dateCaptor.capture(), statusCaptor.capture());
    assertThat(dateCaptor.getValue()).isEqualTo(expectedToday);
    assertThat(statusCaptor.getValue()).isEqualTo(ShiftStatus.CONFIRMED);
  }

  @Test
  void listPublicToday_excludesShiftsWhoseCastIsNotActive() {
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    Shift active =
        Shift.builder()
            .castId("cA")
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(20, 0))
            .build();
    Shift orphan =
        Shift.builder()
            .castId("cGhost")
            .startTime(LocalTime.of(19, 0))
            .endTime(LocalTime.of(21, 0))
            .build();
    when(shiftRepository.findByWorkDateAndStatusAndPublishedTrueOrderByStartTimeAsc(any(), any()))
        .thenReturn(List.of(active, orphan));
    when(castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE"))
        .thenReturn(List.of(activeCast("cA", "キャストA", "urlA")));

    List<PublicShiftResponse> result = shiftService.listPublicToday();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCastId()).isEqualTo("cA");
  }

  @Test
  void listPublicToday_returnsEmptyWhenNoConfirmedShifts() {
    when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of("Asia/Tokyo")));
    when(shiftRepository.findByWorkDateAndStatusAndPublishedTrueOrderByStartTimeAsc(any(), any()))
        .thenReturn(List.of());

    assertThat(shiftService.listPublicToday()).isEmpty();
  }
}
