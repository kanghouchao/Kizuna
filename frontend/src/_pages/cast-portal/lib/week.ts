/** キャストポータルの週表示で使う日付ユーティリティ（日曜起点）。 */

/** Date を 'yyyy-MM-dd'（ローカルタイム基準）に整形する。 */
export function toDateStr(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
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
