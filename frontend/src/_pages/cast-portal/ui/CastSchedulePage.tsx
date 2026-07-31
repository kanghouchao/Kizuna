'use client';

import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { CastScheduleItem, shiftApi } from '@/entities/shift';
import { Badge, Button, Card, CardContent, Input, Label, Textarea } from '@/shared/ui';
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
  const [hasError, setHasError] = useState(false);
  const [change, setChange] = useState<{
    id: string;
    workDate: string;
    startTime: string;
    endTime: string;
    note: string;
  } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setItems(null);
    setHasError(false);
    const dates = weekDates(currentWeekStart);
    shiftApi
      .mySchedule({ from: toDateStr(dates[0]), to: toDateStr(dates[6]) })
      .then(res => {
        if (!cancelled) setItems(res);
      })
      .catch(() => {
        if (!cancelled) setHasError(true);
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

  const openChange = (item: CastScheduleItem) => {
    if (!item.id) return;
    setChange({
      id: item.id,
      workDate: item.work_date ?? '',
      startTime: item.start_time?.slice(0, 5) ?? '',
      endTime: item.end_time?.slice(0, 5) ?? '',
      note: '',
    });
  };

  const submitChange = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!change) return;
    setSubmitting(true);
    try {
      await shiftApi.submitShiftChange({
        target_shift_id: change.id,
        work_date: change.workDate,
        start_time: `${change.startTime}:00`,
        end_time: `${change.endTime}:00`,
        note: change.note || undefined,
      });
      toast.success('変更申請を提出しました');
      setChange(null);
    } catch {
      toast.error('変更申請の提出に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="p-4">
      <div className="mb-4 flex items-center justify-between">
        <Button type="button" variant="outline" size="sm" onClick={() => shiftWeek(-7)}>
          前週
        </Button>
        <p className="text-sm font-medium text-foreground">{rangeLabel}</p>
        <Button type="button" variant="outline" size="sm" onClick={() => shiftWeek(7)}>
          次週
        </Button>
      </div>

      {hasError ? (
        <p className="text-sm text-destructive-strong">スケジュールの取得に失敗しました</p>
      ) : items === null ? (
        <p className="text-sm text-muted-foreground">読み込み中...</p>
      ) : groups.length === 0 ? (
        <p className="text-sm text-muted-foreground">今週の確定シフトはありません</p>
      ) : (
        <div className="space-y-3">
          {groups.map(group => (
            <Card key={group.workDate} className="py-4">
              <CardContent className="px-4">
                <p className="mb-2 text-sm font-semibold text-foreground">
                  {formatDateLabel(group.workDate)}
                </p>
                <ul className="space-y-2">
                  {group.items.map(item => (
                    <li
                      key={item.id ?? `${item.store_id}-${item.start_time}-${item.end_time}`}
                      className="text-sm text-muted-foreground"
                    >
                      <div className="flex items-center justify-between">
                        <Badge
                          variant="outline"
                          className="border-transparent bg-primary/10 text-primary-strong"
                        >
                          {item.store_name}
                        </Badge>
                        <span>
                          {formatTime(item.start_time)}–{formatEndTime(item.end_time)}
                        </span>
                        {item.id && (
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => openChange(item)}
                          >
                            変更申請
                          </Button>
                        )}
                      </div>
                      {item.attendance_confirmed && (
                        <p className="mt-1 text-right text-xs text-success-strong">
                          実績記録済み {formatTime(item.actual_start_time)}–
                          {formatEndTime(item.actual_end_time)}
                        </p>
                      )}
                      {change && change.id === item.id && (
                        <form className="mt-3 grid gap-3" onSubmit={submitChange}>
                          <div className="grid gap-1">
                            <Label htmlFor={`change-date-${item.id}`}>変更後の日付</Label>
                            <Input
                              id={`change-date-${item.id}`}
                              type="date"
                              value={change.workDate}
                              onChange={event =>
                                setChange({ ...change, workDate: event.target.value })
                              }
                              required
                            />
                          </div>
                          <div className="grid grid-cols-2 gap-3">
                            <div className="grid gap-1">
                              <Label htmlFor={`change-start-${item.id}`}>変更後の開始</Label>
                              <Input
                                id={`change-start-${item.id}`}
                                type="time"
                                value={change.startTime}
                                onChange={event =>
                                  setChange({ ...change, startTime: event.target.value })
                                }
                                required
                              />
                            </div>
                            <div className="grid gap-1">
                              <Label htmlFor={`change-end-${item.id}`}>変更後の終了</Label>
                              <Input
                                id={`change-end-${item.id}`}
                                type="time"
                                value={change.endTime}
                                onChange={event =>
                                  setChange({ ...change, endTime: event.target.value })
                                }
                                required
                              />
                            </div>
                          </div>
                          <div className="grid gap-1">
                            <Label htmlFor={`change-note-${item.id}`}>備考</Label>
                            <Textarea
                              id={`change-note-${item.id}`}
                              value={change.note}
                              onChange={event => setChange({ ...change, note: event.target.value })}
                              maxLength={500}
                            />
                          </div>
                          <div className="flex justify-end gap-2">
                            <Button type="button" variant="outline" onClick={() => setChange(null)}>
                              キャンセル
                            </Button>
                            <Button type="submit" disabled={submitting}>
                              {submitting ? '申請中...' : '変更を申請する'}
                            </Button>
                          </div>
                        </form>
                      )}
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
