'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { CastScheduleItem, shiftApi } from '@/entities/shift';
import { formatDate, formatTimeRange, tokyoDate, useResource } from '@/shared/lib';
import {
  Button,
  EmptyState,
  ListRow,
  ListRows,
  ListRowsSkeleton,
  RegionError,
  StoreChips,
} from '@/shared/ui';
import { groupByWorkDate } from '../lib/groupSchedule';
import { toDateStr, weekDates, weekStart } from '../lib/week';
import { ShiftChangeRequestSheet } from './ShiftChangeRequestSheet';

/** 確定シフトを週ごとに見せる（cast_id 単層自限）。日曜起点の週送り付き。複数店舗に在籍するときは店舗で絞る。 */
export function CastSchedulePage() {
  const today = tokyoDate(new Date());
  const [currentWeekStart, setCurrentWeekStart] = useState(() => weekStart(new Date()));
  const [changeOpen, setChangeOpen] = useState(false);
  const [changeTarget, setChangeTarget] = useState<CastScheduleItem | null>(null);
  const [pickedStoreId, setPickedStoreId] = useState<string | null>(null);

  // 店舗と予定は別々の読み口。店舗が読めないときも予定は絞らずに出せる
  const {
    data: storesData,
    isLoading: storesLoading,
    failure: storesFailure,
    reload: reloadStores,
  } = useResource(() => shiftApi.myStores());
  const stores = (storesData ?? []).map(s => ({
    id: String(s.store_id),
    name: s.store_name ?? '',
  }));
  const selectedStoreId = stores.some(s => s.id === pickedStoreId)
    ? pickedStoreId
    : (stores[0]?.id ?? null);

  const {
    data: items,
    isLoading,
    failure,
    reload,
  } = useResource(() => {
    const week = weekDates(currentWeekStart);
    return shiftApi.mySchedule({ from: toDateStr(week[0]), to: toDateStr(week[6]) });
  }, [currentWeekStart]);

  const dates = weekDates(currentWeekStart);
  const rangeLabel = `${formatDate(toDateStr(dates[0]), today)}〜${formatDate(toDateStr(dates[6]), today)}`;
  // 在籍店舗（/me/stores）は退店済みを含まないので、絞るのはチップを出す複数店舗のときだけ。
  // 1 店舗なら返った予定をすべて出し、退店済みの店舗の予定を隠さない
  const visibleItems =
    stores.length >= 2
      ? (items ?? []).filter(item => String(item.store_id) === selectedStoreId)
      : (items ?? []);
  const groups = groupByWorkDate(visibleItems);

  const shiftWeek = (deltaDays: number) => {
    setCurrentWeekStart(prev => {
      const next = new Date(prev);
      next.setDate(next.getDate() + deltaDays);
      return next;
    });
  };

  return (
    <div className="space-y-4">
      {storesFailure !== null ? (
        <RegionError message="店舗の取得に失敗しました" onRetry={() => void reloadStores()} />
      ) : (
        <StoreChips stores={stores} value={selectedStoreId} onValueChange={setPickedStoreId} />
      )}

      <div className="flex items-center justify-between gap-2">
        <Button type="button" variant="outline" className="min-h-11" onClick={() => shiftWeek(-7)}>
          <ChevronLeftIcon aria-hidden="true" />
          前週
        </Button>
        <p className="text-center font-medium text-foreground">{rangeLabel}</p>
        <Button type="button" variant="outline" className="min-h-11" onClick={() => shiftWeek(7)}>
          次週
          <ChevronRightIcon aria-hidden="true" />
        </Button>
      </div>

      {/* 読み込み中を先に置く。週送りでは見出しの範囲だけが先に動くので、取得中に items を
          読むと新しい週の見出しの下へ前の週の行が並ぶ。店舗が決まる前も、他店の行が一瞬混ざるので待つ */}
      {isLoading || storesLoading ? (
        <ListRowsSkeleton />
      ) : failure !== null ? (
        <RegionError message="スケジュールの取得に失敗しました" onRetry={() => void reload()} />
      ) : groups.length === 0 ? (
        <EmptyState
          message="この週の出勤予定はありません"
          action={
            <Button variant="outline" className="min-h-11" render={<Link href="/cast/requests" />}>
              出勤希望を出す
            </Button>
          }
        />
      ) : (
        <div className="space-y-4">
          {groups.map(group => {
            const dateLabel = formatDate(group.workDate, today);
            return (
              <section key={group.workDate} className="space-y-2">
                <h2 className="font-semibold text-foreground">{dateLabel}</h2>
                <ListRows label={dateLabel}>
                  {group.items.map(item => (
                    <ListRow
                      key={item.id ?? `${item.store_id}-${item.start_time}-${item.end_time}`}
                      title={
                        <span className="font-medium tabular-nums">
                          {formatTimeRange(item.start_time ?? '', item.end_time ?? '')}
                        </span>
                      }
                      meta={item.store_name}
                      trailing={
                        item.id && (
                          <Button
                            type="button"
                            variant="outline"
                            className="min-h-11"
                            onClick={() => {
                              setChangeTarget(item);
                              setChangeOpen(true);
                            }}
                          >
                            変更申請
                          </Button>
                        )
                      }
                    />
                  ))}
                </ListRows>
              </section>
            );
          })}
        </div>
      )}

      {/* 提出は履歴（希望提出ページ）側に現れるだけでスケジュール自体は変わらないため、成功後の再取得は不要。 */}
      <ShiftChangeRequestSheet
        open={changeOpen}
        item={changeTarget}
        onClose={() => setChangeOpen(false)}
      />
    </div>
  );
}
