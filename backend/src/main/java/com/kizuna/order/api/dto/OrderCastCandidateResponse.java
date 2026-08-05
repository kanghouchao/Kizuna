package com.kizuna.order.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 指名候補 1 件。ドロップダウンに出すのに要る最小限だけを返す（キャスト管理の応答を流用すると招待状態やカスタム項目まで付いてくる）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCastCandidateResponse {
  private String id;
  private String name;
}
