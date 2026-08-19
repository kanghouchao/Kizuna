package com.kizuna.shift.application;

import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.AbsenceResponse;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 欠勤の導出。行は建てず「確定シフトがあり、未取消の実績が 1 行も無い」を都度導出する（ADR 0014）。
 *
 * <p>シフトの勤務日と実績の営業日を同じ日付で突き合わせられるのは、シフト紐づきの実績が営業日を勤務日から 継承するためである。在籍状態は見ない — ADR 0014
 * の述語に無い条件だからである。
 */
@Service
@RequiredArgsConstructor
public class AbsenceService {

  private final ShiftRepository shiftRepository;
  private final AttendanceRepository attendanceRepository;
  private final BusinessDateService businessDateService;
  private final Clock clock;

  /**
   * 指定営業日の欠勤。門は営業日の終了と、当該キャストのその営業日の全確定シフトの<b>最遅</b>予定終了の経過の 二つで、通らなければ空を返す。最遅を採るのは、実績が（キャスト・営業日）に
   * 1 行しか立たない以上、第一枠の 終了だけで宣言すると第二枠が進行中のキャストを欠勤に数えるためである。
   *
   * <p>並びはキャスト id の昇順。行に一意な識別子が無いので、これが全順序の唯一の根拠になる。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public List<AbsenceResponse> list(LocalDate businessDate) {
    if (!businessDateService.hasEnded(businessDate)) {
      return List.of();
    }
    List<Shift> confirmed =
        shiftRepository.findByWorkDateAndStatus(businessDate, ShiftStatus.CONFIRMED);
    if (confirmed.isEmpty()) {
      return List.of();
    }

    Set<String> attendedCastIds =
        attendanceRepository.findCastIdsWithActiveAttendanceOn(businessDate);

    LocalTime dateChangeTime = businessDateService.currentDateChangeTime();
    Map<String, Instant> latestScheduledEndByCast =
        confirmed.stream()
            .collect(
                Collectors.toMap(
                    Shift::getCastId,
                    shift -> scheduledEndInstant(shift, dateChangeTime),
                    (left, right) -> left.isAfter(right) ? left : right,
                    TreeMap::new));

    Instant now = clock.instant();
    return latestScheduledEndByCast.entrySet().stream()
        .filter(entry -> !attendedCastIds.contains(entry.getKey()))
        .filter(entry -> !now.isBefore(entry.getValue()))
        .map(
            entry ->
                AbsenceResponse.builder().castId(entry.getKey()).businessDate(businessDate).build())
        .toList();
  }

  /**
   * 予定終了を瞬時点へ解決する。壁時計どうしで比べると夏時間の戻しの帯で門が巻き戻り、一度出た欠勤が消えて また現れる。曖昧な時刻は早い側のオフセットを採る — {@link
   * BusinessDateService} と同じ規則である。
   */
  private Instant scheduledEndInstant(Shift shift, LocalTime dateChangeTime) {
    return shift.scheduledEndAt(dateChangeTime).atZone(clock.getZone()).toInstant();
  }
}
