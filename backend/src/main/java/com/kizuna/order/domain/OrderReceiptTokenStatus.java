package com.kizuna.order.domain;

/** 伝票トークンの状態。申領の成立はこの遷移そのものが表し、前提状態の消滅が申領の再送を遮断する。 */
public enum OrderReceiptTokenStatus {
  /** 発行済み・未申領。期限内であれば事後帰属に使える。 */
  ISSUED,
  /** 申領済み。同じトークンで二度目の帰属は成立しない。 */
  CLAIMED,
  /** 失効済み。再発行が前の 1 本を殺した状態で、店舗が渡した QR を回収する唯一の手段にあたる。 */
  REVOKED
}
