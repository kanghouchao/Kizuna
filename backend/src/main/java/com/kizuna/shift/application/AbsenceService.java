package com.kizuna.shift.application;

import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.AbsenceResponse;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 欠勤の導出。行は建てず、（キャスト・店舗・営業日）の粒度で「確定シフトがあり、未取消の実績が 1 行も無い」を 都度導出する（ADR 0014）。店舗は {@code @StoreScoped}
 * の文脈が与える。
 *
 * <p>シフト側の {@code work_date} と実績側の {@code business_date} を同じ日付で突き合わせられるのは、シフトに紐づく実績が
 * 営業日を勤務日から継承するためである（AttendanceService が物化時に保証する）。片側だけを別の鍵で数えると、 予定と実績が噛み合わないまま全員が欠勤になる。
 *
 * <p>キャストの在籍状態は見ない。ADR 0014 の述語は「確定シフト ∧ 未取消の実績なし」だけで、退店したキャストの 過去の確定シフトもそのまま欠勤として導出される。
 */
@Service
@RequiredArgsConstructor
public class AbsenceService {

  private final ShiftRepository shiftRepository;
  private final AttendanceRepository attendanceRepository;
  private final BusinessDateService businessDateService;
  private final Clock clock;

  /**
   * 指定営業日の欠勤。門を通らない営業日は空を返す — 「まだ判定できない」と「誰も欠勤していない」を呼び手は 区別できないが、判定を前倒しする方が害が大きい。
   *
   * <p>門は二つある。営業日そのものの終了（この後に飛び込みの記録が来うる）と、当該キャストのその営業日の 全確定シフトの<b>最遅</b>予定終了の経過である。最遅を採るのは、同じ営業日に
   * 2 枠が立つとき第一枠の終了だけで 欠勤を宣言すると、第二枠が進行中のキャストを欠勤に数えてしまうためで、実績は（キャスト・営業日）に 1 行しか
   * 立たないので第二枠の出勤はまだ記録されえない。
   *
   * <p>並びはキャスト id の昇順で固定する。行に一意な識別子が無いので、これが全順序の唯一の根拠になる。
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
        attendanceRepository
            .findByBusinessDateAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(businessDate)
            .stream()
            .map(Attendance::getCastId)
            .collect(Collectors.toSet());

    Map<String, LocalDateTime> latestScheduledEndByCast =
        confirmed.stream()
            .collect(
                Collectors.toMap(
                    Shift::getCastId,
                    Shift::scheduledEndAt,
                    (left, right) -> left.isAfter(right) ? left : right,
                    TreeMap::new));

    LocalDateTime now = LocalDateTime.now(clock);
    return latestScheduledEndByCast.entrySet().stream()
        .filter(entry -> !attendedCastIds.contains(entry.getKey()))
        .filter(entry -> !now.isBefore(entry.getValue()))
        .map(
            entry ->
                AbsenceResponse.builder().castId(entry.getKey()).businessDate(businessDate).build())
        .toList();
  }
}
