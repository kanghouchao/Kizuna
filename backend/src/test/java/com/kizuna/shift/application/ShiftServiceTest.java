package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.api.dto.PublicShiftResponse;
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
        .isInstanceOf(ServiceException.class)
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
    when(shiftMapper.toPatch(req)).thenReturn(new ShiftPatch(null, null, null, null, "CONFIRMED"));

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
        .isInstanceOf(ServiceException.class)
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
        .isInstanceOf(ServiceException.class)
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
  void delete_removes() {
    when(shiftRepository.existsById("s1")).thenReturn(true);
    shiftService.delete("s1");
    verify(shiftRepository).deleteById("s1");
  }

  @Test
  void delete_throwsWhenNotFound() {
    when(shiftRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> shiftService.delete("missing"))
        .isInstanceOf(ServiceException.class)
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
    when(shiftRepository.findByWorkDateAndStatusOrderByStartTimeAsc(any(), any()))
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
    when(shiftRepository.findByWorkDateAndStatusOrderByStartTimeAsc(any(), any()))
        .thenReturn(List.of());

    shiftService.listPublicToday();

    ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
    verify(shiftRepository)
        .findByWorkDateAndStatusOrderByStartTimeAsc(dateCaptor.capture(), statusCaptor.capture());
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
    when(shiftRepository.findByWorkDateAndStatusOrderByStartTimeAsc(any(), any()))
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
    when(shiftRepository.findByWorkDateAndStatusOrderByStartTimeAsc(any(), any()))
        .thenReturn(List.of());

    assertThat(shiftService.listPublicToday()).isEmpty();
  }
}
