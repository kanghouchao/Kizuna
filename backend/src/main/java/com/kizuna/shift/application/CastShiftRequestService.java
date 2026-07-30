package com.kizuna.shift.application;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shift.api.dto.CastShiftRequestResponse;
import com.kizuna.shift.api.dto.ShiftChangeRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestMapper;
import com.kizuna.shift.api.dto.ShiftRequestResponse;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestKind;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人（キャスト）の出勤希望ユースケース（提出・履歴）。
 *
 * <p>提出は StoreContext が確立していない {@code /platform/me} 配下のため、対象店舗への所属は「当該店舗に本人の cast
 * 行が存在すること」で判定し、{@link ShiftRequest} の store_id を明示設定する（{@code @StoreScoped} を経由しないため
 * StoreScopeStampListener の自動採番に頼れない）。
 */
@Service
@RequiredArgsConstructor
public class CastShiftRequestService {

  private static final String CONFIRMED_STATUS = "CONFIRMED";

  private final PlatformUserRepository platformUserRepository;
  private final CastRepository castRepository;
  private final ShiftRequestRepository shiftRequestRepository;
  private final ShiftRepository shiftRepository;
  private final ShiftRequestMapper shiftRequestMapper;
  private final AppProperties appProperties;

  @Transactional
  public ShiftRequestResponse submit(String email, ShiftRequestCreateRequest request) {
    validateSchedule(request.getWorkDate(), request.getStartTime(), request.getEndTime());

    Long userId = resolveUserId(email);
    // 同一店舗に本人の档案が複数並存し得るため、最古の档案 id を決定的に選ぶ（リポジトリが古い順で返す）。
    String castId =
        castRepository.findIdsByPlatformUserIdAndStoreId(userId, request.getStoreId()).stream()
            .findFirst()
            .orElseThrow(() -> new ServiceException("指定店舗に所属していません"));

    ShiftRequest entity =
        ShiftRequest.builder()
            .castId(castId)
            .workDate(request.getWorkDate())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .note(request.getNote())
            .build();
    entity.setStoreId(request.getStoreId());
    return shiftRequestMapper.toResponse(shiftRequestRepository.save(entity));
  }

  /**
   * 確定済みシフトへの変更申請を提出する。
   *
   * <p>対象シフトの所有は「その cast_id が本人の档案集合に含まれること」で判定し、店舗は対象シフトから導出する — 本人ポータルは StoreContext
   * を持たず、リクエスト側が名乗る店舗を検証する手段が無いため。 存在しないシフトと他人のシフトはどちらも同じ NotFound として扱い、id の実在を漏らさない。
   */
  @Transactional
  public ShiftRequestResponse submitChange(String email, ShiftChangeRequestCreateRequest request) {
    validateSchedule(request.getWorkDate(), request.getStartTime(), request.getEndTime());

    Long userId = resolveUserId(email);
    List<String> castIds = castRepository.findIdsByPlatformUserId(userId);
    Shift target =
        shiftRepository
            .findById(request.getTargetShiftId())
            .filter(shift -> castIds.contains(shift.getCastId()))
            .orElseThrow(() -> new NotFoundException("対象のシフトが見つかりません"));
    if (!CONFIRMED_STATUS.equals(target.getStatus())) {
      throw new ServiceException("確定済みのシフトにのみ変更申請できます");
    }

    ShiftRequest entity =
        ShiftRequest.builder()
            .castId(target.getCastId())
            .kind(ShiftRequestKind.CHANGE)
            .targetShiftId(target.getId())
            .workDate(request.getWorkDate())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .note(request.getNote())
            .build();
    entity.setStoreId(target.getStoreId());
    return shiftRequestMapper.toResponse(shiftRequestRepository.save(entity));
  }

  @Transactional(readOnly = true)
  public List<CastShiftRequestResponse> history(String email) {
    Long userId = resolveUserId(email);
    List<String> castIds = castRepository.findIdsByPlatformUserId(userId);
    if (castIds.isEmpty()) {
      return List.of();
    }
    return shiftRequestRepository.findHistoryByCastIds(castIds).stream()
        .map(shiftRequestMapper::toHistoryResponse)
        .toList();
  }

  /** 新規希望・変更申請に共通する日時の妥当性検査。 */
  private void validateSchedule(LocalDate workDate, LocalTime startTime, LocalTime endTime) {
    if (startTime.equals(endTime)) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }
    LocalDate today = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
    if (workDate.isBefore(today)) {
      throw new ServiceException("勤務日は本日以降を指定してください");
    }
  }

  private Long resolveUserId(String email) {
    return platformUserRepository
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }
}
