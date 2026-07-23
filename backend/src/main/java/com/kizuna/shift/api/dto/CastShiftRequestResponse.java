package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 本人（キャスト）ポータル出勤希望履歴の1件。店舗名を埋め込む。JSON キーは snake_case 設定に従う。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastShiftRequestResponse {
  private String id;
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String note;
  private String status;
  private Long storeId;
  private String storeName;
  private OffsetDateTime createdAt;
}
