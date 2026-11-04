package com.kizuna.remuneration.domain;

import java.util.Comparator;
import java.util.List;

/** 日の承諾の表示状態。保存せず、読むたびに現在の内容と承諾行から導く。 */
public record DayAcceptance(
    AcceptanceState state,
    NotAcceptableReason reason,
    String contentDigest,
    StatementAcceptance latest) {

  /**
   * 承諾不可 → 未承諾 → 承諾済み → 承諾後に変更あり の順で止まる。承諾済みは最新の行でなく任意の行で判定する — 一意制約で同じ写しは二度入らないので、A → B → A
   * と戻った日を最新の行で見ると永久に変更ありへ留まる。
   */
  public static DayAcceptance resolve(
      StatementContent content, boolean businessDayEnded, List<StatementAcceptance> acceptances) {
    var latest =
        acceptances.stream()
            .max(
                Comparator.comparing(StatementAcceptance::getAcceptedAt)
                    .thenComparing(
                        StatementAcceptance::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);
    var reason = content.notAcceptableReason(businessDayEnded);
    if (reason != null)
      return new DayAcceptance(AcceptanceState.NOT_ACCEPTABLE, reason, null, latest);
    var digest = content.digest();
    AcceptanceState state;
    if (latest == null) state = AcceptanceState.NOT_ACCEPTED;
    else if (acceptances.stream().anyMatch(a -> a.getContentDigest().equals(digest)))
      state = AcceptanceState.ACCEPTED;
    else state = AcceptanceState.CHANGED_AFTER_ACCEPTANCE;
    return new DayAcceptance(state, null, digest, latest);
  }
}
