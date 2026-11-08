// 会員に見せるポイントの書式。金額（formatMoney）と同じく負はマイナス記号（U+2212）で出す。

/** 残高「1,200 ポイント」。 */
export function formatPoints(points: number): string {
  return `${points.toLocaleString('ja-JP')} ポイント`;
}

/** 増減「+500 ポイント」「−300 ポイント」。加算と減算が同じ一覧に並ぶので、符号を省かない。 */
export function formatPointChange(points: number): string {
  const sign = points > 0 ? '+' : points < 0 ? '−' : '';
  return `${sign}${formatPoints(Math.abs(points))}`;
}
