/** キャストポータルの週表示で使う日付・時刻ユーティリティ（日曜起点）。 */

/** Date を 'yyyy-MM-dd'（ローカルタイム基準）に整形する。 */
export function toDateStr(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
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
export function formatTime(time: string): string {
  return time.slice(0, 5);
}

/** 終了時刻の表示。00:00 終了は跨夜の連続表記として 24:00 と表示する。 */
export function formatEndTime(time: string): string {
  const hm = time.slice(0, 5);
  return hm === '00:00' ? '24:00' : hm;
}
