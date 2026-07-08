package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.application.CastService;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.api.dto.ShiftResponse;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftPatch;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

  @Mock private ShiftRepository shiftRepository;
  @Mock private ShiftMapper shiftMapper;
  @Mock private TenantContext tenantContext;
  @Mock private TenantRepository tenantRepository;
  @Mock private CastService castService;

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
  void create_setsTenantIdAndSaves() {
    ShiftCreateRequest req = validCreateRequest();

    Shift entity = Shift.builder().castId("c1").status("TENTATIVE").build();
    Tenant tenant = new Tenant();
    tenant.setId(1L);

    when(castService.existsForCurrentTenant("c1")).thenReturn(true);
    when(shiftMapper.toEntity(req)).thenReturn(entity);
    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
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
    assertThat(entity.getTenantId()).isEqualTo(1L);
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
  void create_throwsWhenTenantNotFound() {
    ShiftCreateRequest req = validCreateRequest();

    when(castService.existsForCurrentTenant("c1")).thenReturn(true);
    when(shiftMapper.toEntity(req)).thenReturn(Shift.builder().build());
    when(tenantContext.getTenantId()).thenReturn(1L);
    when(tenantRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shiftService.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("テナントが見つかりません");
  }

  @Test
  void create_rejectsWhenCastNotInTenant() {
    ShiftCreateRequest req = validCreateRequest();

    when(castService.existsForCurrentTenant("c1")).thenReturn(false);

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
  void update_rejectsWhenCastNotInTenant() {
    Shift s = Shift.builder().castId("c1").build();
    s.setId("s1");
    when(shiftRepository.findById("s1")).thenReturn(Optional.of(s));
    when(castService.existsForCurrentTenant("foreign")).thenReturn(false);

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
}
