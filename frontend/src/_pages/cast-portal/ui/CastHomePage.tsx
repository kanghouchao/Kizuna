'use client';

import Link from 'next/link';
import { shiftApi } from '@/entities/shift';
import { formatTimeRange, tokyoDate, useResource } from '@/shared/lib';
import {
  EmptyState,
  ListRow,
  ListRows,
  ListRowsSkeleton,
  Money,
  RegionError,
  Skeleton,
  StatusBadge,
} from '@/shared/ui';
import { useCastHome } from '../model/castHome';

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  const id = `cast-home-${title}`;
  return (
    <section aria-labelledby={id} className="space-y-2">
      <h2 id={id} className="font-semibold">
        {title}
      </h2>
      {children}
    </section>
  );
}

function TodayShift() {
  const today = tokyoDate(new Date());
  const { data, failure, reload } = useResource(() =>
    shiftApi.mySchedule({ from: today, to: today })
  );
  if (failure !== null)
    return <RegionError message="今日の出勤の取得に失敗しました" onRetry={() => void reload()} />;
  if (data === null) return <Skeleton role="status" aria-label="読み込み中" className="h-28" />;
  if (data.length === 0)
    return (
      <EmptyState
        message="今日の出勤はありません"
        action={
          <Link href="/cast/schedule" className="font-medium text-primary-strong underline">
            スケジュールを見る
          </Link>
        }
      />
    );
  return (
    <ul className="space-y-3">
      {data.map(item => (
        <li
          key={item.id ?? `${item.store_id}-${item.start_time}`}
          className="rounded-2xl border bg-card p-5 text-card-foreground shadow-sm"
        >
          <p className="text-sm wrap-anywhere text-muted-foreground">{item.store_name}</p>
          {item.start_time && item.end_time && (
            <p className="mt-1 text-3xl font-bold tabular-nums">
              {formatTimeRange(item.start_time, item.end_time)}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}

function Todos() {
  const { data: items, failed, retry } = useCastHome().todos;
  if (failed) return <RegionError message="やることの取得に失敗しました" onRetry={retry} />;
  if (items === null) return <ListRowsSkeleton rows={2} />;
  if (items.length === 0) return <EmptyState message="いま対応することはありません" />;
  return (
    <ListRows label="やること">
      {items.map(item => (
        <ListRow
          key={item.key}
          title={item.title}
          meta={item.storeName}
          trailing={<StatusBadge status={item.status} />}
          href={item.href}
        />
      ))}
    </ListRows>
  );
}

function MonthTotals() {
  const { data, failed, retry } = useCastHome().monthTotals;
  if (failed) return <RegionError message="今月の報酬の取得に失敗しました" onRetry={retry} />;
  if (data === null) return <ListRowsSkeleton rows={1} />;
  if (data.length === 0) return <EmptyState message="在籍中の店舗がありません" />;
  return (
    <ListRows label="今月の累計報酬">
      {data.map(row => (
        <ListRow
          key={row.storeId}
          title={row.storeName}
          trailing={
            row.total === undefined ? (
              <span className="text-muted-foreground">計算待ち</span>
            ) : (
              <Money amount={row.total} className="text-2xl font-bold" />
            )
          }
        />
      ))}
    </ListRows>
  );
}

/**
 * キャストのホーム。上から 今日の出勤 → やること → 今月の累計報酬（DESIGN.md「本人ポータル」）。
 * 店舗からのお知らせの区画は情報源ができるまで描画しない。
 */
export function CastHomePage() {
  return (
    <div className="space-y-6">
      <Section title="今日の出勤">
        <TodayShift />
      </Section>
      <Section title="やること">
        <Todos />
      </Section>
      <Section title="今月の累計報酬">
        <MonthTotals />
      </Section>
    </div>
  );
}
