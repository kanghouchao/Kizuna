package com.kizuna.shift.application;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shift.api.dto.CastScheduleResponse;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人（キャスト）ポータルの週間スケジュールユースケース。
 *
 * <p>隔離は本人の cast_id に限ることだけで担う — 本人の cast 行は所属店にしか存在しないため、本人に限れば店舗も限られる。リクエストパラメータで本人を 指定させず、認証済み
 * email から本人の cast_id 集合を逆引きする。
 */
@Service
@RequiredArgsConstructor
public class CastScheduleService {

  private static final int MAX_SPAN_DAYS = 31;

  private final UserRepository userRepository;
  private final CastEnrollmentRepository castRepository;
  private final ShiftRepository shiftRepository;
  private final ShiftMapper shiftMapper;

  @Transactional(readOnly = true)
  public List<CastScheduleResponse> myWeek(String email, LocalDate from, LocalDate to) {
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS) {
      throw new ServiceException("取得範囲が不正です");
    }
    Long userId =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
            .getId();
    List<String> castIds = castRepository.findIdsByUserId(userId);
    if (castIds.isEmpty()) {
      return List.of();
    }
    return shiftRepository.findConfirmedSchedule(castIds, from, to).stream()
        .map(shiftMapper::toScheduleResponse)
        .toList();
  }
}
