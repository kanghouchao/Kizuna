'use client';

import { useEffect, useState } from 'react';
import { CastScheduleItem, shiftApi } from '@/entities/shift';
import { groupByWorkDate } from '../lib/groupSchedule';
import {
  formatEndTime,
  formatTime,
  parseDateStr,
  toDateStr,
  weekDates,
  weekStart,
} from '../lib/week';

const WEEKDAY_LABELS = ['日', '月', '火', '水', '木', '金', '土'];

/** 'yyyy-MM-dd' → '7/20（月）' 表示。 */
function formatDateLabel(dateStr: string): string {
  const [, m, d] = dateStr.split('-');
  const weekday = WEEKDAY_LABELS[parseDateStr(dateStr).getDay()];
  return `${Number(m)}/${Number(d)}（${weekday}）`;
}

/** 全所属店の確定シフトを週集約で表示する（cast_id 単層自限）。日曜起点の週ナビ付き。 */
export function CastSchedulePage() {
  const [currentWeekStart, setCurrentWeekStart] = useState(() => weekStart(new Date()));
  const [items, setItems] = useState<CastScheduleItem[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    setItems(null);
    const dates = weekDates(currentWeekStart);
    shiftApi
      .mySchedule({ from: toDateStr(dates[0]), to: toDateStr(dates[6]) })
      .then(res => {
        if (!cancelled) setItems(res);
      })
      .catch(() => {
        if (!cancelled) setItems([]);
      });
    return () => {
      cancelled = true;
    };
  }, [currentWeekStart]);

  const dates = weekDates(currentWeekStart);
  const rangeLabel = `${formatDateLabel(toDateStr(dates[0]))} 〜 ${formatDateLabel(toDateStr(dates[6]))}`;
  const groups = items ? groupByWorkDate(items) : [];

  const shiftWeek = (deltaDays: number) => {
    setCurrentWeekStart(prev => {
      const next = new Date(prev);
      next.setDate(next.getDate() + deltaDays);
      return next;
    });
  };

  return (
    <div className="p-4">
      <div className="mb-4 flex items-center justify-between">
        <button
          type="button"
          onClick={() => shiftWeek(-7)}
          className="rounded-[10px] border border-gray-200 bg-white px-3 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          前週
        </button>
        <p className="text-sm font-medium text-gray-900">{rangeLabel}</p>
        <button
          type="button"
          onClick={() => shiftWeek(7)}
          className="rounded-[10px] border border-gray-200 bg-white px-3 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          次週
        </button>
      </div>

      {items === null ? (
        <p className="text-sm text-gray-500">読み込み中...</p>
      ) : groups.length === 0 ? (
        <p className="text-sm text-gray-500">今週の確定シフトはありません</p>
      ) : (
        <div className="space-y-3">
          {groups.map(group => (
            <div
              key={group.workDate}
              className="rounded-[10px] border border-gray-200 bg-white p-4 shadow-sm"
            >
              <p className="mb-2 text-sm font-semibold text-gray-900">
                {formatDateLabel(group.workDate)}
              </p>
              <ul className="space-y-2">
                {group.items.map(item => (
                  <li
                    key={`${item.store_id}-${item.start_time}`}
                    className="flex items-center justify-between text-sm text-gray-600"
                  >
                    <span className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-600">
                      {item.store_name}
                    </span>
                    <span>
                      {formatTime(item.start_time)}–{formatEndTime(item.end_time)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
