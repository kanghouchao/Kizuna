package com.kizuna.order.domain;

import java.util.List;

/**
 * 会計の試算結果。受注が採用候補を取り込んだ後の姿を値として持ち、集約のインスタンスは外へ出さない。
 *
 * <p>金額・分数・報酬は {@link Order} の計算を通った値で、ここでは再計算しない。ポイントの利用と付与は台帳の見込み（{@code
 * OrderPoints}）が持ち、利用の結果は明細のポイント利用行と合計に現れる。
 */
public record OrderQuote(
    String orderId,
    OrderStatus status,
    OrderCourse course,
    List<OrderFeeLineDraft> feeLines,
    List<SpecialServiceSnapshot> specialServices,
    int totalFee,
    int grantBasisAmount,
    int totalDurationMinutes,
    int totalRemuneration) {

  /** 採用へ渡す、店舗が編集できる明細。 */
  public List<OrderFeeLineDraft> editableFeeLines() {
    return feeLines.stream().filter(line -> line.kind().isStoreEditable()).toList();
  }

  public void requireRedeemable(int usedPoints, boolean memberLinked) {
    Order.requireRedeemable(grantBasisAmount, usedPoints, memberLinked);
  }
}
