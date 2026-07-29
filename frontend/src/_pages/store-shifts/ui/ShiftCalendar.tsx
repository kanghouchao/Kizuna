'use client';

import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { ShiftResponse } from '@/entities/shift';
import { Button } from '@/shared/ui';
import { monthGrid, toDateStr } from '../lib/datetime';

interface ShiftCalendarProps {
  month: Date;
  shifts: ShiftResponse[];
  onPrevMonth: () => void;
  onNextMonth: () => void;
  /** 日セルのクリックで 'yyyy-MM-dd' を通知する（タイムラインへ遷移）。 */
  onSelectDate: (date: string) => void;
}

const WEEKDAYS = ['日', '月', '火', '水', '木', '金', '土'];

/** 月の出勤件数を俯瞰するカレンダー。日クリックでその日のタイムラインへ遷移する。 */
export function ShiftCalendar({
  month,
  shifts,
  onPrevMonth,
  onNextMonth,
  onSelectDate,
}: ShiftCalendarProps) {
  const byDate = new Map<string, { total: number; confirmed: number }>();
  for (const s of shifts) {
    const agg = byDate.get(s.work_date) ?? { total: 0, confirmed: 0 };
    agg.total += 1;
    if (s.status === 'CONFIRMED') agg.confirmed += 1;
    byDate.set(s.work_date, agg);
  }

  const days = monthGrid(month);
  const today = toDateStr(new Date());
  const title = `${month.getFullYear()}年${month.getMonth() + 1}月`;

  return (
    <div className="overflow-hidden rounded-lg border bg-card shadow-sm">
      <div className="flex items-center justify-between border-b px-6 py-4">
        <h2 className="text-lg font-semibold text-foreground">{title}</h2>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={onPrevMonth}
            aria-label="前の月"
          >
            <ChevronLeftIcon />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={onNextMonth}
            aria-label="次の月"
          >
            <ChevronRightIcon />
          </Button>
        </div>
      </div>

      {/* 曜日帯は地の塗りを持たない: 週末色と平日の注記色はいずれもカード面でのみ既定のコントラストを満たす。 */}
      <div className="grid grid-cols-7 border-b">
        {WEEKDAYS.map((w, i) => (
          <div
            key={w}
            className={`px-3 py-2.5 text-center text-xs font-medium ${
              i === 0
                ? 'text-destructive'
                : i === 6
                  ? 'text-primary-strong'
                  : 'text-muted-foreground'
            }`}
          >
            {w}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7">
        {days.map(({ date, inMonth }) => {
          const ds = toDateStr(date);
          const agg = byDate.get(ds);
          const isToday = ds === today;
          const tentative = agg ? agg.total - agg.confirmed : 0;
          return (
            <button
              type="button"
              key={ds}
              onClick={() => onSelectDate(ds)}
              className="group h-28 border-b border-r p-3 text-left align-top transition-colors hover:bg-primary/10 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-primary"
            >
              {/* 当月外はホバーの色地に載るため、注記色のままだと沈む。ホバー時だけ本文色へ上げる。 */}
              <span
                className={`text-sm ${
                  isToday
                    ? 'flex h-6 w-6 items-center justify-center rounded-full bg-primary font-semibold text-primary-foreground'
                    : inMonth
                      ? 'text-foreground'
                      : 'text-muted-foreground group-hover:text-foreground'
                }`}
              >
                {date.getDate()}
              </span>
              {agg && (
                <div className="mt-1 space-y-1">
                  {/* ホバー時はチップ自身の淡色地が同色の地に重なり、primary 系の文字色では
                      コントラストが 4.5 を割る。重なる間だけ本文色へ上げる。 */}
                  <span className="inline-flex items-center rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary-strong group-hover:text-foreground">
                    {agg.total}名
                  </span>
                  <div className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-[10px] text-muted-foreground group-hover:text-foreground">
                    <span className="inline-flex items-center gap-0.5">
                      <span className="inline-block h-1.5 w-1.5 rounded-full bg-success" />
                      確定
                      {agg.confirmed}
                    </span>
                    {tentative > 0 && (
                      <span className="inline-flex items-center gap-0.5">
                        <span className="inline-block h-1.5 w-1.5 rounded-full bg-warning" />
                        未確定
                        {tentative}
                      </span>
                    )}
                  </div>
                </div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
