package com.kizuna.customer.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店舗の顧客から見たポイント残高。残高は顧客ではなく紐づく会員の台帳が持つ。
 *
 * <p>未紐づけの顧客には台帳そのものが存在するに至らないため、残高は 0 ではなく「無い」。non_null 包含により項目ごと応答から落ちる。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPointBalanceResponse {

  /** 顧客が会員に紐づいているか。偽なら残高は載らない。 */
  private boolean linked;

  /** 紐づく会員の現在残高。未紐づけでは null。台帳の合計は 1 件の仕訳と違い int を超えうる。 */
  private Long balance;
}
