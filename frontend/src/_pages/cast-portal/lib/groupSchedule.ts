import { CastScheduleItem } from '@/entities/shift';

export interface ScheduleDayGroup {
  workDate: string;
  items: CastScheduleItem[];
}

/** work_date ごとにグループ化し、日付昇順で返す（同日内の順序は入力順＝サーバ側の start_time 昇順を保つ）。 */
export function groupByWorkDate(items: CastScheduleItem[]): ScheduleDayGroup[] {
  const byDate = new Map<string, CastScheduleItem[]>();
  for (const item of items) {
    const bucket = byDate.get(item.work_date);
    if (bucket) {
      bucket.push(item);
    } else {
      byDate.set(item.work_date, [item]);
    }
  }
  return [...byDate.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([workDate, dayItems]) => ({ workDate, items: dayItems }));
}
