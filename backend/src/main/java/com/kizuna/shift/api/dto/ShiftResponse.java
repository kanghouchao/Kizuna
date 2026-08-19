package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftResponse {
  private String id;
  private String castId;
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;

  /**
   * 予定の開始・終了を暦日付きで写した値。勤務日は暦日ではなく営業日なので、日付変更時刻より前に始まる枠は翌暦日に来る。
   *
   * <p>導出であって列ではない。読み手に勤務日と時刻を自分で組ませないために載せる — 日付変更時刻は {@code PERM_SYSTEM_CONFIG_MANAGE}
   * の管理下で、店舗側の読み手には見えない。
   */
  private LocalDateTime scheduledStartAt;

  private LocalDateTime scheduledEndAt;

  private String status;

  /** 店外への露出可否。店舗側の読み口は値を載せるだけで、行の絞り込みには使わない（ADR 0015 の負向不変量）。 */
  private boolean published;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
