package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 導出された欠勤 1 件。行は建てず、（キャスト・店舗・営業日）の粒度で都度導出する（ADR 0014）ため、 識別子も更新時刻も持たない。店舗は文脈から決まるので載せない。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceResponse {
  private String castId;
  private LocalDate businessDate;
}
