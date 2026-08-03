package com.kizuna.shift.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.domain.ShiftRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 確定シフトの店舗外向け読み口。会員ポータルの指名候補提示と、申請時の指名の妥当性検証が共有する。
 *
 * <p>呼び手（会員）は店舗文脈を確立できないため {@code @StoreScoped} は用いず、店舗の隔離は問い合わせの storeId を明示指定することで担保する —
 * storeFilter は働かないので、この明示指定が唯一の境界である。
 */
@Service
@RequiredArgsConstructor
@NamedInterface("application")
public class ConfirmedShiftLookupService {

  /** 予約申請で参照できる先の上限日数。無制限の未来日を引かせないための保護。 */
  private static final int MAX_LOOKAHEAD_DAYS = 90;

  private static final String CONFIRMED = "CONFIRMED";

  private final ShiftRepository shiftRepository;
  private final StoreExistenceCheck storeExistenceCheck;
  private final AppProperties appProperties;

  /** 指定店舗・指定日の確定シフトに入っている ACTIVE キャストを返す。 */
  @Transactional(readOnly = true)
  public List<PublicShiftResponse> listConfirmedCasts(Long storeId, LocalDate workDate) {
    if (!storeExistenceCheck.exists(storeId)) {
      throw new ServiceException("店舗が見つかりません");
    }
    validateWorkDate(workDate);
    return shiftRepository.findConfirmedCasts(storeId, workDate).stream()
        .map(
            view ->
                PublicShiftResponse.builder()
                    .castId(view.getCastId())
                    .castName(view.getCastName())
                    .castPhotoUrl(view.getCastPhotoUrl())
                    .startTime(view.getStartTime())
                    .endTime(view.getEndTime())
                    .build())
        .toList();
  }

  /** 指定店舗・指定キャスト・指定日に確定シフトがあるか。 */
  @Transactional(readOnly = true)
  public boolean hasConfirmedShift(Long storeId, String castId, LocalDate workDate) {
    return shiftRepository.existsByStoreIdAndCastIdAndWorkDateAndStatus(
        storeId, castId, workDate, CONFIRMED);
  }

  private void validateWorkDate(LocalDate workDate) {
    LocalDate today = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
    if (workDate.isBefore(today) || ChronoUnit.DAYS.between(today, workDate) > MAX_LOOKAHEAD_DAYS) {
      throw new ServiceException("取得できる日付の範囲を超えています");
    }
  }
}
