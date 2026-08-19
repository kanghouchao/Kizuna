'use client';

import { ChevronLeftIcon, ChevronRightIcon, EyeIcon, EyeOffIcon, PlusIcon } from 'lucide-react';
import { CastResponse } from '@/entities/cast';
import { ShiftResponse } from '@/entities/shift';
import { Button, Input, RegionError } from '@/shared/ui';
import { addDaysStr, hhmm, shiftSpan, toDateStr } from '../lib/datetime';
import { castName, shiftLabel } from '../lib/labels';
import { isUnpublished } from '../lib/publication';

interface ShiftTimelineProps {
  /** 表示日 'yyyy-MM-dd'。 */
  date: string;
  shifts: ShiftResponse[];
  casts: CastResponse[];
  loading: boolean;
  /** 取得失敗。ヘッダ（日付ナビ）は残し、本体だけが失敗を名乗る — 別の日へ動くことが復旧経路を兼ねる。 */
  failed: boolean;
  onRetry: () => void;
  onChangeDate: (date: string) => void;
  onAddShift: () => void;
  onEditShift: (shift: ShiftResponse) => void;
  /** 公開可否の切替中。逐行更新の途中で押し増されると、どの行がどちらへ向かうか読めなくなる。 */
  publishing: boolean;
  onChangePublication: (targets: ShiftResponse[], published: boolean) => void;
}

/** クイックジャンプは選択中の日ではなく実際の今日を基準にする。 */
const QUICK_JUMPS = [
  { label: '昨日', offset: -1 },
  { label: '今日', offset: 0 },
  { label: '明日', offset: 1 },
  { label: '明後日', offset: 2 },
] as const;

const SLOT_MINUTES = 30;
const LABEL_COL = 'w-28';
const LABEL_OFFSET = 'ml-28';

/** その日の最早出勤〜最遅終了に自動フィットする出勤タイムライン。 */
export function ShiftTimeline({
  date,
  shifts,
  casts,
  loading,
  failed,
  onRetry,
  onChangeDate,
  onAddShift,
  onEditShift,
  publishing,
  onChangePublication,
}: ShiftTimelineProps) {
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
    <div className="rounded-lg border bg-card shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <h2 className="text-lg font-semibold text-foreground">{date} の出勤</h2>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1">
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={() => onChangeDate(addDaysStr(date, -1))}
              aria-label="前日"
            >
              <ChevronLeftIcon />
            </Button>
            {/* クリアで空文字が飛んでくるため無視する（表示日が無い状態は作らない） */}
            <Input
              type="date"
              value={date}
              onChange={e => {
                if (e.target.value) onChangeDate(e.target.value);
              }}
              aria-label="表示する日付"
              className="w-40"
            />
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={() => onChangeDate(addDaysStr(date, 1))}
              aria-label="翌日"
            >
              <ChevronRightIcon />
            </Button>
          </div>
          <div className="flex items-center gap-1">
            {QUICK_JUMPS.map(({ label, offset }) => (
              <Button
                key={label}
                type="button"
                variant="outline"
                size="sm"
                onClick={() => onChangeDate(addDaysStr(toDateStr(new Date()), offset))}
              >
                {label}
              </Button>
            ))}
          </div>
          <Button type="button" onClick={onAddShift}>
            <PlusIcon />
            シフト追加
          </Button>
        </div>
      </div>

      {failed ? (
        <RegionError
          message="シフトの取得に失敗しました"
          onRetry={onRetry}
          className="justify-center p-8"
        />
      ) : loading ? (
        <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
      ) : !hasShifts ? (
        <div className="p-12 text-center">
          <p className="text-muted-foreground">この日のシフトはまだありません。</p>
          <Button
            type="button"
            variant="link"
            onClick={onAddShift}
            className="mt-3 h-auto p-0 text-sm font-medium text-primary-strong"
          >
            シフトを追加する
          </Button>
        </div>
      ) : (
        <div className="space-y-4 p-6">
          {/* 同時出勤数カバレッジ */}
          <div>
            <div className="mb-1 flex items-center justify-between text-xs text-muted-foreground">
              <span>同時出勤数</span>
              <span>ピーク {peak}名</span>
            </div>
            <div className={`flex h-10 items-end gap-px ${LABEL_OFFSET}`}>
              {coverage.map(c => (
                <div
                  key={c.at}
                  className="flex-1 rounded-t bg-primary-strong"
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
                  className="absolute -translate-x-1/2 text-[10px] text-muted-foreground"
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
                      className={`${LABEL_COL} shrink-0 truncate pr-2 text-sm font-medium text-foreground`}
                    >
                      {castName(casts, castId)}
                    </div>
                    <div className="relative h-9 flex-1 rounded bg-muted">
                      {hourMarks.map(m => (
                        <div
                          key={m}
                          className="absolute top-0 h-full border-l"
                          style={{ left: `${pct(m)}%` }}
                        />
                      ))}
                      {rowShifts.map(s => {
                        const { start, end } = shiftSpan(s.start_time, s.end_time);
                        const confirmed = s.status === 'CONFIRMED';
                        const hidden = isUnpublished(s);
                        const label = shiftLabel(s, casts);
                        return (
                          // 目玉はバーの中に置くが、編集の口とは別の押しどころなので入れ子の
                          // ボタンにはできない。バーの見た目を持つ器に、二つのボタンを並べる。
                          <div
                            key={s.id}
                            className={`absolute top-1 flex h-7 items-center overflow-hidden rounded text-xs font-medium shadow-sm ${
                              !confirmed
                                ? 'bg-warning text-warning-foreground hover:bg-warning/90'
                                : hidden
                                  ? 'border-2 border-dashed border-success-strong text-foreground hover:bg-success/10'
                                  : 'bg-success text-success-foreground hover:bg-success/90'
                            }`}
                            style={{
                              left: `${pct(start)}%`,
                              width: `${Math.max(pct(end) - pct(start), 4)}%`,
                            }}
                            title={label}
                          >
                            <button
                              type="button"
                              onClick={() => onEditShift(s)}
                              className="min-w-0 flex-1 truncate px-2 text-left"
                              aria-label={`${label} を編集`}
                            >
                              {hhmm(s.start_time)}–{hhmm(s.end_time)}
                            </button>
                            {/* 仮シフトは目玉を持たない（理由は lib/publication.ts）。
                                焦点輪はブラウザ既定のまま — バーの中に ring の載る認証済みの面が
                                無く（bg-muted 上の ring-primary は暗色で 2.83）、overflow-hidden が
                                外側の輪を切る。隣の編集ボタンも同じ既定に従う。 */}
                            {confirmed && (
                              <button
                                type="button"
                                disabled={publishing}
                                onClick={() => onChangePublication([s], hidden)}
                                className="shrink-0 px-1.5 disabled:opacity-50"
                                aria-label={`${label} を${hidden ? '公開する' : '非公開にする'}`}
                              >
                                {hidden ? (
                                  <EyeOffIcon className="h-3.5 w-3.5" />
                                ) : (
                                  <EyeIcon className="h-3.5 w-3.5" />
                                )}
                              </button>
                            )}
                          </div>
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
                  className="absolute top-0 bottom-0 w-px bg-destructive"
                  style={{ left: `${pct(nowMin)}%` }}
                >
                  <span className="absolute -top-0.5 -translate-x-1/2 rounded bg-destructive px-1 text-[9px] font-medium text-destructive-foreground">
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
