package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * シフト 1 本の詳細＝系列の全体。希望・変更申請（shift_id の背骨で結ばれた申請）と当日実績を、 状態ごとの実行主体・日時付きで内联する。
 *
 * <p>一覧の {@link ShiftResponse} と分けるのは、系列が一覧の全行に要る情報ではないためである。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftDetailResponse {
  private String id;
  private String castId;
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String status;

  /** 店外への露出可否。店舗側の読み口は値を載せるだけで、行の絞り込みには使わない（ADR 0015 の負向不変量）。 */
  private boolean published;

  /** 承認で生まれた行なら承認者。 */
  private ActorResponse createdBy;

  /** 最後に書き換えた実行者。作成のみの行では null。 */
  private ActorResponse updatedBy;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  /** 出生（NEW）と変更申請（CHANGE）の履歴を古い順に。店舗が直接作成したシフトでは空。 */
  private List<ShiftRequestLineageResponse> requests;

  /** 未取消の当日実績。未記録・取消済みしか無い場合は null。 */
  private AttendanceLineageResponse attendance;
}
