'use client';

import { useEffect, useState } from 'react';
import { CastScheduleItem, shiftApi } from '@/entities/shift';
import { Badge, Button, Card, CardContent } from '@/shared/ui';
import { groupByWorkDate } from '../lib/groupSchedule';
import { ShiftChangeRequestModal } from './ShiftChangeRequestModal';
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
  const [changeTarget, setChangeTarget] = useState<CastScheduleItem | null>(null);

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
                      className="flex items-center justify-between text-sm text-muted-foreground"
                    >
                      <Badge
                        variant="outline"
                        className="border-transparent bg-primary/10 text-primary-strong"
                      >
                        {item.store_name}
                      </Badge>
                      <span className="flex items-center gap-2">
                        {formatTime(item.start_time)}–{formatEndTime(item.end_time)}
                        {item.id && (
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setChangeTarget(item)}
                          >
                            変更申請
                          </Button>
                        )}
                      </span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* 提出は履歴（希望提出ページ）側に現れるだけでスケジュール自体は変わらないため、成功後の再取得は不要。 */}
      <ShiftChangeRequestModal item={changeTarget} onClose={() => setChangeTarget(null)} />
    </div>
  );
}
