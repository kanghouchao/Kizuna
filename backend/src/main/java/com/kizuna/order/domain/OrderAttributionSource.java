package com.kizuna.order.domain;

/**
 * 帰属が成立した機構。帰属が生まれる瞬間はこの 2 つだけで、成立後は関連の状態に影響されない。
 *
 * <p>純粋な事実の記録であり、値によって挙動を分けない。
 */
public enum OrderAttributionSource {
  /** 完了の瞬間。完了時の会員解決（顧客 → 有効な関連 → 会員）が会員に達した。 */
  COMPLETION,
  /** 事後申領の瞬間。伝票トークンの所持証明による受注 1 件の取り戻し。 */
  RECEIPT_TOKEN
}
