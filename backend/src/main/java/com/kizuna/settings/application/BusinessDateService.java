package com.kizuna.settings.application;

import java.time.Clock;
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
    return businessDateOf(LocalDateTime.now(clock));
  }

  /**
   * 暦日付き時刻が属する営業日。時刻だけでは決まらないため、呼び手は暦日を伴う値を渡す。
   *
   * <p>比較は壁時計の時刻で行う。夏時間の戻し（同じ壁時計時刻が 2 度来る帯）に日付変更時刻が入る タイムゾーンでは営業日が一時的に巻き戻るが、{@code app.timezone}
   * は夏時間を持たない日本時間で、 実需が出るまで重なりの扱いは定義しない。
   */
  public LocalDate businessDateOf(LocalDateTime at) {
    return at.toLocalTime().isBefore(dateChangeTime())
        ? at.toLocalDate().minusDays(1)
        : at.toLocalDate();
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

  /** 未設定・不正値は 00:00（暦日と一致）へ倒す。更新時に TIME として検証済みのため不正値は通常は到達しない。 */
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
