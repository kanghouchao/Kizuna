package com.kizuna.order.domain;

/**
 * 受注一覧の並び替えの鍵。並びは全群（作業キュー・アーカイブ）へ同じものを当てるため、鍵の定義はここ 1 箇所に持つ。
 *
 * <p>鍵は<b>受注自身の列に限る</b>。join 先の顧客名・キャスト名を鍵にすると、並びが他の集約の書き換えで動く。
 *
 * <p>どの鍵も<b>未設定を最大として</b>並べる（昇順で末尾、降順で先頭）。到着予定時刻・人数・コース時間はいずれも可空で、 素の列をそのまま鍵にするとカーソルの比較（{@code 鍵 >
 * :位置}）が NULL 行に対して常に不成立になり、 <b>先頭ページ以降その行へ二度と到達できなくなる</b>。値を持たない行を集めるための番兵であって、実在の値と衝突しても副キー id が
 * 全順序を保つので害はない。
 *
 * <p>時刻を「その日の何分目か」に均すのは、番兵に整数リテラルだけを使うため。秒だけ違う 2 件は同値に畳まれるが、そこは id が解く。
 */
public enum OrderSortKey {
  BUSINESS_DATE("o.businessDate", KeyType.DATE),
  ARRIVAL_TIME(
      "coalesce(hour(o.arrivalScheduledStartTime) * 60 + minute(o.arrivalScheduledStartTime),"
          + " 2147483647)",
      KeyType.NUMBER),
  PAX("coalesce(o.pax, 2147483647)", KeyType.NUMBER),
  COURSE_MINUTES("coalesce(o.courseMinutes, 2147483647)", KeyType.NUMBER);

  /** 鍵の値の型。カーソルの符号化・復号がこの型に従う。 */
  public enum KeyType {
    DATE,
    NUMBER
  }

  /** 未設定を最大として扱うための番兵。{@link #ARRIVAL_TIME} は分に均した値なので同じ整数で足りる。 */
  private static final int UNSET_SENTINEL = Integer.MAX_VALUE;

  private final String expression;
  private final KeyType keyType;

  OrderSortKey(String expression, KeyType keyType) {
    this.expression = expression;
    this.keyType = keyType;
  }

  /**
   * 並び替えに使う JPQL 式（別名 {@code o} の受注を前提とする）。
   *
   * <p>ORDER BY とカーソルの比較は<b>必ずこの同じ式</b>を使う。片方だけ素の列にすると、並びと位置の判定がずれて 続きが手前へ戻るか行を飛ばす。
   */
  public String expression() {
    return expression;
  }

  public KeyType keyType() {
    return keyType;
  }

  /** 1 行から「続きの位置」の鍵を取り出す。{@link #expression()} が SQL 側で計算する値と同じものを Java 側で作る。 */
  public String cursorValueOf(OrderView view) {
    return switch (this) {
      case BUSINESS_DATE -> view.getBusinessDate().toString();
      case ARRIVAL_TIME ->
          String.valueOf(
              view.getArrivalScheduledStartTime() == null
                  ? UNSET_SENTINEL
                  : view.getArrivalScheduledStartTime().getHour() * 60
                      + view.getArrivalScheduledStartTime().getMinute());
      case PAX -> String.valueOf(view.getPax() == null ? UNSET_SENTINEL : view.getPax());
      case COURSE_MINUTES ->
          String.valueOf(
              view.getCourseMinutes() == null ? UNSET_SENTINEL : view.getCourseMinutes());
    };
  }
}
