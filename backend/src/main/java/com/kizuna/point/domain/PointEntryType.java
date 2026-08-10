package com.kizuna.point.domain;

/**
 * ポイント台帳の仕訳種別。
 *
 * <p>加算になりうるのは {@link #ORDER_GRANT} と {@link #MANUAL_ADJUST} だけで、残りは必ず減算。符号は {@code amount}
 * が持ち、種別は「なぜ動いたか」だけを表す。
 */
public enum PointEntryType {

  /** 受注完了に伴う付与。 */
  ORDER_GRANT,

  /** 受注会計でのポイント利用。 */
  USE,

  /** 運用者による手動調整（加算・減算の両方）。 */
  MANUAL_ADJUST,

  /** 加算仕訳の取消（未消費の残りを打ち消す）。 */
  CANCEL,

  /** 有効期限切れによる失効。 */
  EXPIRE,

  /** 退会に伴う残高消去。 */
  WITHDRAWAL_CLEAR
}
