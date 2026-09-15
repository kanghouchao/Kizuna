package com.kizuna.order.api.dto;

import com.kizuna.order.domain.OrderFeeLineKind;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 明細維持・手入力・設定選択のいずれか一つを運ぶ。組合せは採用時に検証する。 */
@Data
public class OrderFeeLineRequest {
  private String lineId;
  private OrderFeeLineKind kind;

  @Size(max = 255, message = "明細の名称は255文字以内です")
  private String name;

  private Integer amount;
  private Integer durationMinutes;
  private Integer remuneration;
  private String serviceId;
  private String revisionId;
}
