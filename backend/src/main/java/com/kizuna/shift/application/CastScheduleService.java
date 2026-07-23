package com.kizuna.shift.application;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.api.dto.CastScheduleResponse;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人（キャスト）ポータルの週間スケジュールユースケース。
 *
 * <p>隔離は cast_id 単層自限のみで {@code @StoreSetScoped}/{@code @StoreScoped} は用いない — 本人の cast
 * 行は所属店にしか存在しないため、本人自限が同時に店舗自限としても機能する。リクエストパラメータで本人を指定させず、認証済み email から 本人の cast_id 集合を逆引きする。
 */
@Service
@RequiredArgsConstructor
public class CastScheduleService {

  private final PlatformUserRepository platformUserRepository;
  private final CastRepository castRepository;
  private final ShiftRepository shiftRepository;
  private final ShiftMapper shiftMapper;

  @Transactional(readOnly = true)
  public List<CastScheduleResponse> myWeek(String email, LocalDate from, LocalDate to) {
    Long userId =
        platformUserRepository
            .findByEmail(email)
            .orElseThrow(() -> new ServiceException("ユーザーが見つかりません"))
            .getId();
    List<String> castIds = castRepository.findIdsByPlatformUserId(userId);
    if (castIds.isEmpty()) {
      return List.of();
    }
    return shiftRepository.findConfirmedSchedule(castIds, from, to).stream()
        .map(shiftMapper::toScheduleResponse)
        .toList();
  }
}
