package com.kizuna.order.domain;

/**
 * 受注一覧の並び替えの鍵。並びは全群（作業キュー・アーカイブ）へ同じものを当てるため、鍵の定義はここ 1 箇所に持つ。
 *
 * <p>鍵は<b>受注自身の列に限る</b>。join 先の顧客名・キャスト名を鍵にすると、並びが他の集約の書き換えで動く。
 *
 * <p>どの鍵も<b>未設定を最大として</b>並べる（昇順で末尾、降順で先頭）。可空の列は番兵へ寄せ、実在の値と衝突しても副キー id が
 * 全順序を保つ。時刻を「その日の何分目か」に均すのは、番兵に整数リテラルだけを使うため。
 */
public enum OrderSortKey {
  BUSINESS_DATE("o.businessDate"),
  ARRIVAL_TIME(
      "coalesce(hour(o.arrivalScheduledStartTime) * 60 + minute(o.arrivalScheduledStartTime),"
          + " 2147483647)"),
  PAX("coalesce(o.pax, 2147483647)"),
  COURSE_MINUTES("coalesce(o.course.durationMinutes, 2147483647)");

  private final String expression;

  OrderSortKey(String expression) {
    this.expression = expression;
  }

  /** 並び替えに使う JPQL 式（別名 {@code o} の受注を前提とする）。 */
  public String expression() {
    return expression;
  }
}
