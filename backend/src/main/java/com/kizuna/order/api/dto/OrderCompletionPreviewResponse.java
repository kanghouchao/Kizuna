package com.kizuna.order.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 受注完了の事前計算。入力された会計金額に対して、その受注で何ポイント付与され、何ポイントまで利用できるかを返す。
 *
 * <p>残高は会員に紐づく顧客の受注でのみ意味を持つため、未紐づけの受注では載せない（非会員にポイントは存在しない）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCompletionPreviewResponse {

  /** 受注の顧客が会員に紐づいているか。偽ならポイントの利用も付与も起こらない。 */
  private boolean memberLinked;

  /** 会員の現在残高。未紐づけでは null。台帳の合計は 1 件の仕訳と違い int を超えうる。 */
  private Long pointBalance;

  /** ポイント利用の単位。 */
  private int usageUnit;

  /** この会計金額で付与されるポイント数。 */
  private int grantPoints;
}
