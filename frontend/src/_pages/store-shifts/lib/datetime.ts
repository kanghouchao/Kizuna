/** 出勤管理ページ内で共有する日付・時刻ユーティリティ。 */

/** Date を 'yyyy-MM-dd'（ローカルタイム基準）に整形する。 */
export function toDateStr(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** その月の初日〜末日を 'yyyy-MM-dd' の区間で返す。 */
export function monthRange(month: Date): { from: string; to: string } {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const last = new Date(month.getFullYear(), month.getMonth() + 1, 0);
  return { from: toDateStr(first), to: toDateStr(last) };
}

/** 月カレンダーのグリッド（日曜開始・6 週 = 42 セル）。各セルは日付と当月フラグを持つ。 */
export function monthGrid(month: Date): { date: Date; inMonth: boolean }[] {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const start = new Date(first);
  start.setDate(first.getDate() - first.getDay());
  const days: { date: Date; inMonth: boolean }[] = [];
  for (let i = 0; i < 42; i++) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    days.push({ date: d, inMonth: d.getMonth() === month.getMonth() });
  }
  return days;
}

/** 'HH:mm:ss' または 'HH:mm' を深夜 0 時からの分に変換する。 */
export function timeToMinutes(time: string): number {
  const [h, m] = time.split(':');
  return Number(h) * 60 + Number(m);
}

/** シフトの [開始分, 終了分]。終了 <= 開始 は翌日にまたがる勤務として +1440 する。 */
export function shiftSpan(startTime: string, endTime: string): { start: number; end: number } {
  const start = timeToMinutes(startTime);
  let end = timeToMinutes(endTime);
  if (end <= start) end += 24 * 60;
  return { start, end };
}
