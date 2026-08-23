package com.kizuna.order.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 訂正前の明細行 1 行の写し（{@code t_order_corrections.fee_lines} の要素）。行の同一性が無い明細を鎖で復元するため、前値は行集合ごと保持する。
 *
 * <p>金額は保存されているとおりの帯符号で、表示上の値へは翻さない — 前値は記録であって画面ではない。
 *
 * <p>入力側の {@link OrderFeeLineDraft} と形は同じだが別型で持つ。あちらは差し替えの入力で、こちらは永続化された jsonb の形であり、
 * 片方の項目名を直すともう片方の既存の行が読めなくなる。
 *
 * <p>hypersistence-utils は dirty checking 用の深いコピーを Java 直列化で作るため、要素型は Serializable である必要がある。
 */
public record OrderFeeLineSnapshot(OrderFeeLineKind kind, String name, Integer amount)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  static OrderFeeLineSnapshot of(OrderFeeLine line) {
    return new OrderFeeLineSnapshot(line.getKind(), line.getName(), line.getAmount());
  }
}
