package com.kizuna.order.application;

import java.util.List;

/** 会計の確定。確認値は同じ内容を試算した証明で、試算と完了の双方がこの型を署名の入力にする。 */
public record OrderCompletionCommand(
    Long expectedVersion,
    List<OrderFeeLineInput> feeLines,
    Integer usePoints,
    String confirmationToken) {

  public int usedPoints() {
    return usePoints == null ? 0 : usePoints;
  }
}
