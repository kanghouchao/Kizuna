package com.kizuna.point.domain;

/**
 * ポイント台帳の仕訳種別。符号は {@code amount} が持ち、種別は「なぜ動いたか」と<b>取りうる向き</b>を表す。
 *
 * <p>向きを種別に持たせるのは、加算になりうる種別が増えたときに「受注を根拠とする付与がすべて追跡・取消できる」（#381 要件 3）の 見直しを機械的に促すためである。{@code
 * PointEntryTypeTest} が枚挙を固定しており、加算になりうる種別を足すと赤になる。
 */
public enum PointEntryType {

  /** 受注完了に伴う付与。 */
  ORDER_GRANT(Direction.CREDIT),

  /** 受注会計でのポイント利用。 */
  USE(Direction.DEBIT),

  /** 運用者による手動調整（加算・減算の両方）。 */
  MANUAL_ADJUST(Direction.BOTH),

  /** 加算仕訳の取消（未消費の残りを打ち消す）。 */
  CANCEL(Direction.DEBIT),

  /**
   * 利用の取消（巻き戻し）。元の利用を参照し、引き当てを逆転して消費量を元のロットへ返す。
   *
   * <p>加算だが<b>新しいロットにはならない</b>。返した先のロットが期限を持ち続けるので、再付与型（新規ロット）にすると 期限付きポイントが無期限へ洗い替わる。
   */
  USE_CANCEL(Direction.CREDIT),

  /** 有効期限切れによる失効。 */
  EXPIRE(Direction.DEBIT),

  /** 退会に伴う残高消去。 */
  WITHDRAWAL_CLEAR(Direction.DEBIT);

  private final Direction direction;

  PointEntryType(Direction direction) {
    this.direction = direction;
  }

  /** 加算（正の増減）の仕訳になりうるか。 */
  public boolean creditable() {
    return direction != Direction.DEBIT;
  }

  /** 減算（負の増減）の仕訳になりうるか。 */
  public boolean debitable() {
    return direction != Direction.CREDIT;
  }

  private enum Direction {
    CREDIT,
    DEBIT,
    BOTH
  }
}
