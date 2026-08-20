package com.kizuna.order.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 受注明細 1 行の応答。金額は入力と同じく<b>表示上の値</b>で、減項は正値で返る（符号は種別が表す）。
 *
 * <p>{@code systemOwned} が真の行は完了処理が台帳仕訳と対で書いた記録で、店舗の通常編集では差し替えられない。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFeeLineResponse {
  private String kind;
  private String name;
  private Integer amount;
  private boolean systemOwned;
}
