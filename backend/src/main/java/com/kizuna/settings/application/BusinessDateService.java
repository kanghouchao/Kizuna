package com.kizuna.settings.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 営業日（暦日から独立した帰属単位）の判定。プラットフォーム統一の日付変更時刻で区切り、帰属は分割しない。
 *
 * <p>日付変更時刻の変更は将来の判定にのみ作用する（不遡及 — ADR 0014）。物化済みの営業日は書き換えない。
 */
@Service
@RequiredArgsConstructor
public class BusinessDateService {

  static final String DATE_CHANGE_TIME_KEY = "business_date_change_time";

  private final SystemConfigService systemConfigService;
  private final Clock clock;

  /** 現在の営業日。日付変更時刻より前の深夜帯では前の暦日を返す。 */
  public LocalDate currentBusinessDate() {
    return businessDateOf(clock.instant());
  }

  /**
   * 利用者が記入した暦日付き時刻が属する営業日。時刻だけでは決まらないため、呼び手は暦日を伴う値を渡す。
   *
   * <p>夏時間の戻しで同じ壁時計時刻が 2 度来る帯では、先に来る方（早い側のオフセット）として解決する。 壁時計の記入からはどちらの回かを区別できないため、選び直せる規則を 1
   * つ決めておく。
   */
  public LocalDate businessDateOf(LocalDateTime at) {
    return businessDateOf(at.atZone(clock.getZone()).toInstant());
  }

  /**
   * 瞬時点が属する営業日。境界は暦日ごとの日付変更時刻を瞬時点へ解決してから比べる。
   *
   * <p>壁時計の時刻同士で比べると、夏時間の戻しの帯で営業日が巻き戻る — 同じ壁時計時刻が 2 度来るため、
   * 後から来た瞬時点の方が「日付変更時刻より前」に見える。瞬時点で比べれば時間の進みと同じ向きにしか動かない。
   */
  private LocalDate businessDateOf(Instant at) {
    LocalDate calendarDate = at.atZone(clock.getZone()).toLocalDate();
    return at.isBefore(dateChangeInstant(calendarDate)) ? calendarDate.minusDays(1) : calendarDate;
  }

  /** その暦日の日付変更時刻が指す瞬時点。存在しない時刻（夏時間の進み）は前へ送られ、2 度来る時刻は早い側のオフセットを採る。 */
  private Instant dateChangeInstant(LocalDate calendarDate) {
    return calendarDate.atTime(dateChangeTime()).atZone(clock.getZone()).toInstant();
  }

  /**
   * その営業日が終了したか（現在の営業日より前か）。
   *
   * <p>見るのは営業日の境界だけで、個々のシフトの予定終了時刻は見ない — 欠勤導出は営業日の終了に加えて 当該シフトの予定終了の経過も要求する（ADR
   * 0014）ので、この述語だけでは足りない。
   */
  public boolean hasEnded(LocalDate businessDate) {
    return businessDate.isBefore(currentBusinessDate());
  }

  /**
   * 未設定・不正値は 00:00（暦日と一致）へ倒す。更新時に TIME として検証済みのため不正値は通常は到達しない。
   *
   * <p>読み側は ISO のまま緩く解釈する（分精度の強制は書き口が担う）— DB を直に編集された秒付きの値は 一意に解釈できるので、それを 00:00
   * へ倒して境界を丸ごと動かす方が害が大きい。
   */
  private LocalTime dateChangeTime() {
    String raw = systemConfigService.getConfigValue(DATE_CHANGE_TIME_KEY).orElse("");
    if (raw.isBlank()) {
      return LocalTime.MIDNIGHT;
    }
    try {
      return LocalTime.parse(raw.trim());
    } catch (DateTimeParseException e) {
      return LocalTime.MIDNIGHT;
    }
  }
}
