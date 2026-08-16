package com.kizuna.settings.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 設定更新の要求本体。宛先の設定キーは path が持つため、本体は値だけを運ぶ。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigUpdateRequest {
  private String configValue;
}
