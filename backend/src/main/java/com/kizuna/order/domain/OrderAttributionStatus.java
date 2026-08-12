package com.kizuna.order.domain;

/** 帰属記録の状態。有効な帰属は受注 1 件につき高々 1 件で、部分一意索引がこの値を述語に取る。 */
public enum OrderAttributionStatus {
  /** 有効。会員の来店履歴に現れる。 */
  ACTIVE,
  /** 無効化済み。誤帰属の訂正として理由付きで人手が倒した記録で、行は削除しない。 */
  INVALIDATED
}
