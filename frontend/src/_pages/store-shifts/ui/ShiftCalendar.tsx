'use client';

import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/react/24/outline';
import { ShiftResponse } from '@/entities/shift';
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
    <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
        <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onPrevMonth}
            aria-label="前の月"
            className="rounded-md p-2 text-gray-500 hover:bg-gray-50"
          >
            <ChevronLeftIcon className="h-5 w-5" />
          </button>
          <button
            type="button"
            onClick={onNextMonth}
            aria-label="次の月"
            className="rounded-md p-2 text-gray-500 hover:bg-gray-50"
          >
            <ChevronRightIcon className="h-5 w-5" />
          </button>
        </div>
      </div>

      <div className="grid grid-cols-7 border-b border-gray-200 bg-gray-50">
        {WEEKDAYS.map((w, i) => (
          <div
            key={w}
            className={`px-2 py-2 text-center text-xs font-medium ${
              i === 0 ? 'text-red-500' : i === 6 ? 'text-blue-500' : 'text-gray-500'
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
              className={`h-24 border-b border-r border-gray-100 p-2 text-left align-top transition-colors hover:bg-blue-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-500 ${
                inMonth ? 'bg-white' : 'bg-gray-50'
              }`}
            >
              <span
                className={`text-sm ${
                  isToday
                    ? 'flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 font-semibold text-white'
                    : inMonth
                      ? 'text-gray-900'
                      : 'text-gray-400'
                }`}
              >
                {date.getDate()}
              </span>
              {agg && (
                <div className="mt-1 space-y-1">
                  <span className="inline-flex items-center rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                    {agg.total}名
                  </span>
                  <div className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-[10px] text-gray-500">
                    <span className="inline-flex items-center gap-0.5">
                      <span className="inline-block h-1.5 w-1.5 rounded-full bg-green-500" />
                      確定
                      {agg.confirmed}
                    </span>
                    {tentative > 0 && (
                      <span className="inline-flex items-center gap-0.5">
                        <span className="inline-block h-1.5 w-1.5 rounded-full bg-yellow-400" />
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
