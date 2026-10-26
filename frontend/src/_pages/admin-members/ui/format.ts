/** 日時の表示。応答は ISO 文字列で、業務のタイムゾーンで読ませる。 */
export function formatDateTime(value: string | undefined): string {
  if (!value) return '—';
  return new Date(value).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' });
}

/** 増減は符号を付けて出す。加算と減算が同じ一本に並ぶので、符号が無いとどちらか読めない。 */
export function formatSignedPoints(amount: number): string {
  return `${amount > 0 ? '+' : ''}${amount.toLocaleString('ja-JP')}`;
}
