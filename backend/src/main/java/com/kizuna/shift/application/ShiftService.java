package com.kizuna.shift.application;

import com.kizuna.cast.application.CastService;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.api.dto.ShiftResponse;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.tenant.domain.TenantRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftService {

  private final ShiftRepository shiftRepository;
  private final ShiftMapper shiftMapper;
  private final TenantContext tenantContext;
  private final TenantRepository tenantRepository;
  private final CastService castService;

  @TenantScoped
  @Transactional(readOnly = true)
  public List<ShiftResponse> list(LocalDate from, LocalDate to) {
    return shiftRepository.findByWorkDateBetween(from, to).stream()
        .map(shiftMapper::toResponse)
        .toList();
  }

  @TenantScoped
  @Transactional
  public ShiftResponse create(ShiftCreateRequest request) {
    if (request.getStartTime().equals(request.getEndTime())) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }
    if (!castService.existsForCurrentTenant(request.getCastId())) {
      throw new ServiceException("キャストが見つかりません: " + request.getCastId());
    }

    Shift shift = shiftMapper.toEntity(request);

    shift.setTenantId(
        tenantRepository
            .findById(tenantContext.getTenantId())
            .orElseThrow(() -> new ServiceException("テナントが見つかりません"))
            .getId());

    return shiftMapper.toResponse(shiftRepository.save(shift));
  }

  @TenantScoped
  @Transactional
  public ShiftResponse update(String id, ShiftUpdateRequest request) {
    Shift shift =
        shiftRepository.findById(id).orElseThrow(() -> new ServiceException("シフトが見つかりません: " + id));

    if (request.getCastId() != null && !castService.existsForCurrentTenant(request.getCastId())) {
      throw new ServiceException("キャストが見つかりません: " + request.getCastId());
    }

    // 部分更新のマージ結果（実効の開始・終了）で判定する。片方だけ来て既存値と一致する穴を塞ぐ。
    LocalTime effectiveStart =
        request.getStartTime() != null ? request.getStartTime() : shift.getStartTime();
    LocalTime effectiveEnd =
        request.getEndTime() != null ? request.getEndTime() : shift.getEndTime();
    if (effectiveStart != null && effectiveStart.equals(effectiveEnd)) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }

    shift.apply(shiftMapper.toPatch(request));

    return shiftMapper.toResponse(shiftRepository.save(shift));
  }

  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!shiftRepository.existsById(id)) {
      throw new ServiceException("シフトが見つかりません: " + id);
    }
    shiftRepository.deleteById(id);
  }
}
