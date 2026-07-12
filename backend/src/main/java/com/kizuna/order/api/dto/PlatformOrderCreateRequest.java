package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 平台からの明示的単店指定の受注作成リクエスト（#323）。店舗（tenant）を storeId で明示する以外は {@link OrderCreateRequest} と同一。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformOrderCreateRequest extends OrderCreateRequest {

  @NotNull(message = "店舗IDは必須です")
  private Long storeId;
}
