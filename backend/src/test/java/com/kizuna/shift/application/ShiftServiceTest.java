package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.api.dto.ShiftActualRequest;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.api.dto.ShiftResponse;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftPatch;
import com.kizuna.shift.domain.ShiftRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

  @Mock private ShiftRepository shiftRepository;
  @Mock private ShiftMapper shiftMapper;
  @Mock private CastService castService;
  @Mock private CastRepository castRepository;
  @Mock private AppProperties appProperties;
  @Mock private StoreContext storeContext;

  @InjectMocks private ShiftService shiftService;

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

    Shift entity = Shift.builder().castId("c1").status("TENTATIVE").build();

    when(castService.existsForCurrentStore("c1")).thenReturn(true);
    when(shiftMapper.toEntity(req)).thenReturn(entity);
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

    ShiftResponse res = shiftService.create(req);
    assertThat(res.getId()).isEqualTo("s_new");
  }

  @Test
  void create_rejectsWhenEndEqualsStart() {
    ShiftCreateRequest req = validCreateRequest();
    req.setStartTime(LocalTime.of(20, 0));
    req.setEndTime(LocalTime.of(20, 0));

    assertThatThrownBy(() -> shiftService.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("開始時刻と終了時刻");
  }

  @Test
  void create_rejectsWhenCastNotInStore() {
    ShiftCreateRequest req = validCreateRequest();

    when(castService.existsForCurrentStore("c1")).thenReturn(false);

    assertThatThrownBy(() -> shiftService.create(req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("キャストが見つかりません");
  }

  @Test
  void update_appliesPatchAndSaves() {
    Shift s = Shift.builder().castId("c1").status("TENTATIVE").build();
    s.setId("s1");

    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    when(shiftRepository.save(any())).thenReturn(s);

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setStatus("CONFIRMED");
    when(shiftMapper.toPatch(req))
        .thenReturn(new ShiftPatch(null, null, null, null, "CONFIRMED", null));

    ShiftResponse resp = new ShiftResponse();
    resp.setStatus("CONFIRMED");
    when(shiftMapper.toResponse(s)).thenReturn(resp);

    ShiftResponse result = shiftService.update("s1", req);
    assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    assertThat(s.getStatus()).isEqualTo("CONFIRMED");
  }

  @Test
  void update_throwsWhenNotFound() {
    when(shiftRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftService.update("missing", new ShiftUpdateRequest()))
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

    assertThatThrownBy(() -> shiftService.update("s1", req))
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

    assertThatThrownBy(() -> shiftService.update("s1", req))
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

    assertThatThrownBy(() -> shiftService.update("s1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("開始時刻と終了時刻");
  }

  @Test
  void create_rejectsInvalidStatus() {
    ShiftCreateRequest req = validCreateRequest();
    req.setStatus("BOGUS");

    assertThatThrownBy(() -> shiftService.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("不正なステータス");
  }

  @Test
  void update_rejectsInvalidStatus() {
    Shift s = Shift.builder().castId("c1").build();
    s.setId("s1");
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));

    ShiftUpdateRequest req = new ShiftUpdateRequest();
    req.setStatus("BOGUS");

    assertThatThrownBy(() -> shiftService.update("s1", req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("不正なステータス");
  }

  @Test
  void update_changesPublicationWithoutChangingConfirmationOrPlannedTimes() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.of(2026, 7, 8))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status("CONFIRMED")
            .publicVisible(true)
            .build();
    shift.setId("s1");
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));
    when(shiftRepository.save(shift)).thenReturn(shift);

    ShiftUpdateRequest request = new ShiftUpdateRequest();
    request.setPublicVisible(false);
    when(shiftMapper.toPatch(request))
        .thenReturn(new ShiftPatch(null, null, null, null, null, false));
    when(shiftMapper.toResponse(shift)).thenReturn(new ShiftResponse());

    shiftService.update("s1", request);

    assertThat(shift.isPublicVisible()).isFalse();
    assertThat(shift.getStatus()).isEqualTo("CONFIRMED");
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(23, 0));
  }

  @Test
  void update_rejectsCastReassignmentAfterActualWasRecorded() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.of(2026, 7, 8))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status("CONFIRMED")
            .build();
    shift.setId("s1");
    shift.recordActual(LocalTime.of(18, 5), LocalTime.of(23, 10), "staff@kizuna.test");
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));

    ShiftUpdateRequest request = new ShiftUpdateRequest();
    request.setCastId("c2");

    assertThatThrownBy(() -> shiftService.update("s1", request))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("実績記録済み");

    verify(shiftRepository, never()).save(any());
    assertThat(shift.getCastId()).isEqualTo("c1");
  }

  @Test
  void update_rejectsStatusDowngradeAfterActualWasRecorded() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.of(2026, 7, 8))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status("CONFIRMED")
            .build();
    shift.setId("s1");
    shift.recordActual(LocalTime.of(18, 5), LocalTime.of(23, 10), "staff@kizuna.test");
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));

    ShiftUpdateRequest request = new ShiftUpdateRequest();
    request.setStatus("TENTATIVE");

    assertThatThrownBy(() -> shiftService.update("s1", request))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("実績記録済み");

    verify(shiftRepository, never()).save(any());
    assertThat(shift.getStatus()).isEqualTo("CONFIRMED");
  }

  @Test
  void recordActual_tracksActualTimesAndActorWithoutChangingPlan() {
    when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.of(2026, 7, 8))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status("CONFIRMED")
            .build();
    shift.setId("s1");
    shift.setStoreId(1L);
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));
    when(storeContext.getStoreId()).thenReturn(1L);
    when(shiftRepository.save(shift)).thenReturn(shift);
    when(shiftMapper.toResponse(shift)).thenReturn(new ShiftResponse());
    ShiftActualRequest request = new ShiftActualRequest();
    request.setStartTime(LocalTime.of(18, 5));
    request.setEndTime(LocalTime.of(23, 10));

    shiftService.recordActual("s1", request, "staff@kizuna.test");

    assertThat(shift.isAttendanceConfirmed()).isTrue();
    assertThat(shift.getActualStartTime()).isEqualTo(LocalTime.of(18, 5));
    assertThat(shift.getActualEndTime()).isEqualTo(LocalTime.of(23, 10));
    assertThat(shift.getActualRecordedBy()).isEqualTo("staff@kizuna.test");
    assertThat(shift.getActualRecordedAt()).isNotNull();
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(23, 0));
  }

  @Test
  void recordActual_rejectsFutureShift() {
    when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Shift shift =
        Shift.builder()
            .castId("c1")
            .workDate(LocalDate.now(ZoneId.of("Asia/Tokyo")).plusDays(1))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status("CONFIRMED")
            .build();
    shift.setId("s1");
    shift.setStoreId(1L);
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));
    when(storeContext.getStoreId()).thenReturn(1L);
    ShiftActualRequest request = new ShiftActualRequest();
    request.setStartTime(LocalTime.of(18, 5));
    request.setEndTime(LocalTime.of(23, 10));

    assertThatThrownBy(() -> shiftService.recordActual("s1", request, "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("未来");

    verify(shiftRepository, never()).save(any());
    assertThat(shift.isAttendanceConfirmed()).isFalse();
  }

  @Test
  void recordActual_rejectsTentativeShift() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status("TENTATIVE")
            .build();
    shift.setId("s1");
    shift.setStoreId(1L);
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));
    when(storeContext.getStoreId()).thenReturn(1L);
    ShiftActualRequest request = new ShiftActualRequest();
    request.setStartTime(LocalTime.of(18, 5));
    request.setEndTime(LocalTime.of(23, 10));

    assertThatThrownBy(() -> shiftService.recordActual("s1", request, "staff@kizuna.test"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("確定済み");
  }

  @Test
  void recordActual_rejectsShiftFromAnotherStoreEvenWhenDirectIdLoadFindsIt() {
    Shift shift =
        Shift.builder()
            .castId("c1")
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status("CONFIRMED")
            .build();
    shift.setId("s1");
    shift.setStoreId(2L);
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(shift));
    when(storeContext.getStoreId()).thenReturn(1L);
    ShiftActualRequest request = new ShiftActualRequest();
    request.setStartTime(LocalTime.of(18, 5));
    request.setEndTime(LocalTime.of(23, 10));

    assertThatThrownBy(() -> shiftService.recordActual("s1", request, "staff@kizuna.test"))
        .isInstanceOf(NotFoundException.class);

    verify(shiftRepository, never()).save(any());
  }

  @Test
  void delete_removes() {
    when(shiftRepository.existsById("s1")).thenReturn(true);
    shiftService.delete("s1");
    verify(shiftRepository).deleteById("s1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(shiftRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> shiftService.delete("missing"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("シフトが見つかりません");
  }

  private Cast activeCast(String id, String name, String photoUrl) {
    Cast cast = Cast.builder().name(name).photoUrl(photoUrl).build();
    cast.setId(id);
    return cast;
  }

  @Test
  void listPublicToday_joinsCastInfoAndPreservesRepoOrder() {
    when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
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
    when(shiftRepository.findByWorkDateAndStatusAndPublicVisibleTrueOrderByStartTimeAsc(
            any(), any()))
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
  void listPublicToday_queriesTodayInConfiguredTimezoneWithConfirmedStatus() {
    when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    LocalDate expectedToday = LocalDate.now(ZoneId.of("Asia/Tokyo"));
    when(shiftRepository.findByWorkDateAndStatusAndPublicVisibleTrueOrderByStartTimeAsc(
            any(), any()))
        .thenReturn(List.of());

    shiftService.listPublicToday();

    ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
    verify(shiftRepository)
        .findByWorkDateAndStatusAndPublicVisibleTrueOrderByStartTimeAsc(
            dateCaptor.capture(), statusCaptor.capture());
    assertThat(dateCaptor.getValue()).isEqualTo(expectedToday);
    assertThat(statusCaptor.getValue()).isEqualTo("CONFIRMED");
  }

  @Test
  void listPublicToday_excludesShiftsWhoseCastIsNotActive() {
    when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
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
    when(shiftRepository.findByWorkDateAndStatusAndPublicVisibleTrueOrderByStartTimeAsc(
            any(), any()))
        .thenReturn(List.of(active, orphan));
    when(castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE"))
        .thenReturn(List.of(activeCast("cA", "キャストA", "urlA")));

    List<PublicShiftResponse> result = shiftService.listPublicToday();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCastId()).isEqualTo("cA");
  }

  @Test
  void listPublicToday_returnsEmptyWhenNoConfirmedShifts() {
    when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    when(shiftRepository.findByWorkDateAndStatusAndPublicVisibleTrueOrderByStartTimeAsc(
            any(), any()))
        .thenReturn(List.of());

    assertThat(shiftService.listPublicToday()).isEmpty();
  }
}
