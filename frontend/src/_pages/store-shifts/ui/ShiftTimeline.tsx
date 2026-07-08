'use client';

import { PlusIcon } from '@heroicons/react/24/outline';
import { CastResponse } from '@/entities/cast';
import { ShiftResponse } from '@/entities/shift';
import { shiftSpan, toDateStr } from '../lib/datetime';

interface ShiftTimelineProps {
  /** 表示日 'yyyy-MM-dd'。 */
  date: string;
  shifts: ShiftResponse[];
  casts: CastResponse[];
  loading: boolean;
  onAddShift: () => void;
  onEditShift: (shift: ShiftResponse) => void;
}

const SLOT_MINUTES = 30;
const LABEL_COL = 'w-28';
const LABEL_OFFSET = 'ml-28';

/** その日の最早出勤〜最遅終了に自動フィットする出勤タイムライン。 */
export function ShiftTimeline({
  date,
  shifts,
  casts,
  loading,
  onAddShift,
  onEditShift,
}: ShiftTimelineProps) {
  const castName = (id: string) => casts.find(c => c.id === id)?.name ?? '不明';
  const fmt = (t: string) => t.slice(0, 5);
  const hourLabel = (min: number) => `${String(Math.floor((min % 1440) / 60)).padStart(2, '0')}:00`;

  const spans = shifts.map(s => shiftSpan(s.start_time, s.end_time));
  const hasShifts = spans.length > 0;

  // 軸レンジ（時間境界へ丸める）。シフトが無い場合は代表的な夜帯を仮表示。
  const minStart = hasShifts ? Math.min(...spans.map(s => s.start)) : 18 * 60;
  const maxEnd = hasShifts ? Math.max(...spans.map(s => s.end)) : 24 * 60;
  const axisStart = Math.floor(minStart / 60) * 60;
  const axisEnd = Math.ceil(maxEnd / 60) * 60;
  const total = Math.max(axisEnd - axisStart, 60);
  const pct = (min: number) => ((min - axisStart) / total) * 100;

  const hourMarks: number[] = [];
  for (let m = axisStart; m <= axisEnd; m += 60) hourMarks.push(m);

  // 同時出勤数（30 分刻み）
  const coverage: { at: number; count: number }[] = [];
  for (let m = axisStart; m < axisEnd; m += SLOT_MINUTES) {
    coverage.push({ at: m, count: spans.filter(s => s.start <= m && m < s.end).length });
  }
  const peak = Math.max(1, ...coverage.map(c => c.count));

  // 現在時刻線（表示日が今日で、軸レンジ内のときだけ）
  const now = new Date();
  const nowMin = now.getHours() * 60 + now.getMinutes();
  const showNow = date === toDateStr(now) && nowMin >= axisStart && nowMin <= axisEnd;

  const rowIds = Array.from(new Set(shifts.map(s => s.cast_id)));

  return (
    <div className="rounded-lg border border-gray-200 bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
        <h2 className="text-lg font-semibold text-gray-900">{date} の出勤</h2>
        <button
          type="button"
          onClick={onAddShift}
          className="inline-flex items-center gap-1.5 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <PlusIcon className="h-5 w-5" />
          シフト追加
        </button>
      </div>

      {loading ? (
        <div className="p-8 text-center text-gray-500">読み込み中...</div>
      ) : !hasShifts ? (
        <div className="p-12 text-center">
          <p className="text-gray-500">この日のシフトはまだありません。</p>
          <button
            type="button"
            onClick={onAddShift}
            className="mt-3 text-sm font-medium text-blue-600 hover:text-blue-700"
          >
            シフトを追加する
          </button>
        </div>
      ) : (
        <div className="space-y-4 p-6">
          {/* 同時出勤数カバレッジ */}
          <div>
            <div className="mb-1 flex items-center justify-between text-xs text-gray-500">
              <span>同時出勤数</span>
              <span>ピーク {peak}名</span>
            </div>
            <div className={`flex h-10 items-end gap-px ${LABEL_OFFSET}`}>
              {coverage.map(c => (
                <div
                  key={c.at}
                  className="flex-1 rounded-t bg-blue-500/80"
                  style={{ height: `${(c.count / peak) * 100}%` }}
                  title={`${hourLabel(c.at)} ${c.count}名`}
                />
              ))}
            </div>
          </div>

          <div className="relative">
            {/* 時間目盛 */}
            <div className={`relative h-5 ${LABEL_OFFSET}`}>
              {hourMarks.map(m => (
                <span
                  key={m}
                  className="absolute -translate-x-1/2 text-[10px] text-gray-400"
                  style={{ left: `${pct(m)}%` }}
                >
                  {hourLabel(m)}
                </span>
              ))}
            </div>

            {/* キャスト行 */}
            <div className="space-y-2">
              {rowIds.map(castId => {
                const rowShifts = shifts.filter(s => s.cast_id === castId);
                return (
                  <div key={castId} className="flex items-center">
                    <div
                      className={`${LABEL_COL} shrink-0 truncate pr-2 text-sm font-medium text-gray-700`}
                    >
                      {castName(castId)}
                    </div>
                    <div className="relative h-9 flex-1 rounded bg-gray-50">
                      {hourMarks.map(m => (
                        <div
                          key={m}
                          className="absolute top-0 h-full border-l border-gray-100"
                          style={{ left: `${pct(m)}%` }}
                        />
                      ))}
                      {rowShifts.map(s => {
                        const { start, end } = shiftSpan(s.start_time, s.end_time);
                        const confirmed = s.status === 'CONFIRMED';
                        return (
                          <button
                            type="button"
                            key={s.id}
                            onClick={() => onEditShift(s)}
                            className={`absolute top-1 flex h-7 items-center overflow-hidden rounded px-2 text-xs font-medium shadow-sm ${
                              confirmed
                                ? 'bg-green-500 text-white hover:bg-green-600'
                                : 'bg-yellow-400 text-yellow-900 hover:bg-yellow-500'
                            }`}
                            style={{
                              left: `${pct(start)}%`,
                              width: `${Math.max(pct(end) - pct(start), 4)}%`,
                            }}
                            title={`${castName(castId)} ${fmt(s.start_time)}–${fmt(s.end_time)}`}
                          >
                            <span className="truncate">
                              {fmt(s.start_time)}–{fmt(s.end_time)}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* 現在時刻線 */}
            {showNow && (
              <div className="pointer-events-none absolute inset-y-0 right-0 left-28">
                <div
                  className="absolute top-0 bottom-0 w-px bg-red-500"
                  style={{ left: `${pct(nowMin)}%` }}
                >
                  <span className="absolute -top-0.5 -translate-x-1/2 rounded bg-red-500 px-1 text-[9px] font-medium text-white">
                    現在
                  </span>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
