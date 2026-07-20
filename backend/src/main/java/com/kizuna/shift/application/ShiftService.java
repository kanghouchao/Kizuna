package com.kizuna.shift.application;

import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.api.dto.ShiftResponse;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.store.domain.StoreRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftService {

  private static final Set<String> ALLOWED_STATUSES = Set.of("TENTATIVE", "CONFIRMED");

  private final ShiftRepository shiftRepository;
  private final ShiftMapper shiftMapper;
  private final StoreContext storeContext;
  private final StoreRepository storeRepository;
  private final CastService castService;
  private final CastRepository castRepository;
  private final AppProperties appProperties;

  @StoreScoped
  @Transactional(readOnly = true)
  public List<ShiftResponse> list(LocalDate from, LocalDate to) {
    return shiftRepository.findByWorkDateBetween(from, to).stream()
        .map(shiftMapper::toResponse)
        .toList();
  }

  /**
   * 公開出勤表用に「本日（app.timezone）」の確定（CONFIRMED）シフトを start_time 昇順で返す。 ACTIVE でないキャストのシフトは公開一覧 ({@code
   * /store/casts/public}) に整合させて除外する。cast 表示情報は公開されている cast.domain（{@link Cast}）を
   * 直接参照して結合する（cast.api.dto は公開面ではないため）。storeFilter は {@code @StoreScoped} によりセッション全体で有効なので t_casts
   * 参照も現店舗に絞られる。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public List<PublicShiftResponse> listPublicToday() {
    LocalDate today = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
    List<Shift> shifts =
        shiftRepository.findByWorkDateAndStatusOrderByStartTimeAsc(today, "CONFIRMED");
    if (shifts.isEmpty()) {
      return List.of();
    }
    Map<String, Cast> activeCasts =
        castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE").stream()
            .collect(Collectors.toMap(Cast::getId, Function.identity()));
    return shifts.stream()
        .filter(shift -> activeCasts.containsKey(shift.getCastId()))
        .map(
            shift -> {
              Cast cast = activeCasts.get(shift.getCastId());
              return PublicShiftResponse.builder()
                  .castId(shift.getCastId())
                  .castName(cast.getName())
                  .castPhotoUrl(cast.getPhotoUrl())
                  .startTime(shift.getStartTime())
                  .endTime(shift.getEndTime())
                  .build();
            })
        .toList();
  }

  @StoreScoped
  @Transactional
  public ShiftResponse create(ShiftCreateRequest request) {
    if (request.getStartTime().equals(request.getEndTime())) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }
    validateStatus(request.getStatus());
    if (!castService.existsForCurrentStore(request.getCastId())) {
      throw new ServiceException("キャストが見つかりません: " + request.getCastId());
    }

    Shift shift = shiftMapper.toEntity(request);

    shift.setStoreId(
        storeRepository
            .findById(storeContext.getStoreId())
            .orElseThrow(() -> new ServiceException("店舗が見つかりません"))
            .getId());

    return shiftMapper.toResponse(shiftRepository.save(shift));
  }

  @StoreScoped
  @Transactional
  public ShiftResponse update(String id, ShiftUpdateRequest request) {
    Shift shift =
        shiftRepository.findById(id).orElseThrow(() -> new ServiceException("シフトが見つかりません: " + id));

    validateStatus(request.getStatus());
    if (request.getCastId() != null && !castService.existsForCurrentStore(request.getCastId())) {
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

  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (!shiftRepository.existsById(id)) {
      throw new ServiceException("シフトが見つかりません: " + id);
    }
    shiftRepository.deleteById(id);
  }

  /** status が指定された場合、許可値（TENTATIVE / CONFIRMED）以外を拒否する。null は不変更として許容。 */
  private void validateStatus(String status) {
    if (status != null && !ALLOWED_STATUSES.contains(status)) {
      throw new ServiceException("不正なステータスです: " + status);
    }
  }
}
