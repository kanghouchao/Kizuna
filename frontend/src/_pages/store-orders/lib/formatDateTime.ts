/** 時刻は応答のオフセット付き文字列をそのまま切らず、閲覧者の時間帯へ直して出す（切ると +09:00 が落ちる）。 */
export function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('ja-JP');
}
