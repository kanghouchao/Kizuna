/** キャストポータルの週表示で使う日付・時刻ユーティリティ（日曜起点）。 */

/** Date を 'yyyy-MM-dd'（ローカルタイム基準）に整形する。 */
export function toDateStr(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * 提出フォームで選べる最も古い日付（'yyyy-MM-dd'）。ブラウザの暦日ではなく、その前日を下限にする。
 *
 * 受理の可否を決めるのは営業日（プラットフォームの日付変更時刻で区切る）で、これは暦日に対して最大 1 日遅れる
 * — 日付変更時刻前の深夜帯では前の暦日がまだ現在の営業日である。時刻はサーバだけが知るので、client 側は
 * サーバと食い違わない最も緩い下限だけを持ち、正確な判定はサーバに委ねる。
 */
export function earliestRequestableDate(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return toDateStr(d);
}

/** 'yyyy-MM-dd' をローカルタイムの Date（時刻は 00:00）として構築する。UTC 解釈による曜日ズレを避けるため、日付文字列から曜日を導出する箇所は必ずこれを経由する。 */
export function parseDateStr(dateStr: string): Date {
  const [y, m, d] = dateStr.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** base を含む週の日曜日（週の開始日、時刻は 00:00）を返す。 */
export function weekStart(base: Date): Date {
  const start = new Date(base.getFullYear(), base.getMonth(), base.getDate());
  start.setDate(start.getDate() - start.getDay());
  return start;
}

/** start（日曜日）を起点に 7 日分の日付配列を返す。 */
export function weekDates(start: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return d;
  });
}

/** 'HH:mm:ss' 等 → 'HH:mm' 表示。 */
export function formatTime(time: string | undefined): string {
  return (time ?? '').slice(0, 5);
}

/** 終了時刻の表示。00:00 終了は跨夜の連続表記として 24:00 と表示する。 */
export function formatEndTime(time: string | undefined): string {
  const hm = (time ?? '').slice(0, 5);
  return hm === '00:00' ? '24:00' : hm;
}
