package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系列照会に現れる申請 1 件。NEW はそのシフトの出生、CHANGE は適用された変更の履歴を表す。
 *
 * <p>店舗側 inbox の {@link StoreShiftRequestResponse} と項目が重なるが、あちらは承認判断のための一覧
 * （対象シフトの現況と承認可否を内联する）であり、こちらは確定した 1 本のシフトから過去を辿るための節点である。 承認・却下の実行主体と時刻はこちらにしか無い。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftRequestLineageResponse {
  private String id;
  private String type;
  private String status;

  /** 申請の実行主体はキャスト本人。 */
  private String castId;

  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String note;

  /** 変更申請の提出時点における対象シフトの日時（NEW では null）。 */
  private LocalDate originalWorkDate;

  private LocalTime originalStartTime;
  private LocalTime originalEndTime;

  /** 提出時刻。 */
  private OffsetDateTime createdAt;

  private ActorResponse processedBy;
  private OffsetDateTime processedAt;
}
