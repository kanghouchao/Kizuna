'use client';

import { ArrowDownRightIcon, ArrowUpRightIcon } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts';
import { cn, formatMoney } from '@/shared/lib';
import { Badge, Card, CardHeader, CardTitle, RegionError, Skeleton } from '@/shared/ui';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/shared/ui/chart';
import { useProto, useRegion } from './controls';
import { storeDaily } from './fake-data';

/** 区画の中身の三態。denied の判定（枠ごと出さない）は呼び出し側の案が行う。 */
export function RegionBody({
  region,
  failMessage,
  skeleton,
  children,
}: {
  region: string;
  failMessage: string;
  skeleton: ReactNode;
  children: ReactNode;
}) {
  const { state, retry } = useRegion(region);
  if (state === 'loading') return <>{skeleton}</>;
  if (state === 'error') return <RegionError message={failMessage} onRetry={retry} />;
  return <>{children}</>;
}

export function useVisible() {
  const { states, showCandidates, candidateKeys } = useProto();
  return (key: string) =>
    states[key] !== 'denied' && (showCandidates || !candidateKeys.includes(key));
}

export function StatSkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-4 w-24" />
      <Skeleton className="h-8 w-40" />
      <Skeleton className="h-4 w-32" />
    </div>
  );
}

/** 数字は内訳の一覧への導線（試作では遷移しない）。 */
export function NumLink({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <Link
      href="#"
      className={cn(
        'rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
        className
      )}
    >
      {children}
    </Link>
  );
}

export function Delta({ now, before, label }: { now: number; before: number; label: string }) {
  const rate = ((now - before) / before) * 100;
  const up = rate >= 0;
  const Icon = up ? ArrowUpRightIcon : ArrowDownRightIcon;
  return (
    <p className="flex flex-wrap items-center gap-x-1 text-sm">
      <span
        className={cn(
          'inline-flex items-center gap-0.5 font-medium tabular-nums',
          up ? 'text-success-strong' : 'text-destructive-strong'
        )}
      >
        <Icon className="size-4" aria-hidden />
        {up ? '+' : '−'}
        {Math.abs(rate).toFixed(1)}%
      </span>
      <span className="text-muted-foreground">
        {label} {formatMoney(before)}
      </span>
    </p>
  );
}

/** 統計カードの中身: 注釈・数値・増減の順（DESIGN.md）。 */
export function Stat({
  note,
  value,
  sub,
  delta,
}: {
  note: string;
  value: ReactNode;
  sub?: ReactNode;
  delta?: ReactNode;
}) {
  return (
    <div className="min-w-0 space-y-1">
      <p className="text-sm text-muted-foreground">{note}</p>
      <p className="text-3xl font-bold tabular-nums text-foreground">
        <NumLink>{value}</NumLink>
      </p>
      {sub && <p className="text-sm text-muted-foreground tabular-nums">{sub}</p>}
      {delta}
    </div>
  );
}

const chartConfig = {
  sales: { label: '売上', color: 'var(--primary-strong)' },
} satisfies ChartConfig;

export function DailyChart({ className }: { className?: string }) {
  return (
    <ChartContainer config={chartConfig} className={cn('aspect-auto h-56 w-full', className)}>
      <BarChart data={storeDaily} margin={{ left: 4, right: 4, top: 8 }} barCategoryGap={2}>
        <CartesianGrid vertical={false} />
        <XAxis
          dataKey="day"
          tickLine={false}
          axisLine={false}
          tickMargin={8}
          tickFormatter={d => `${d}日`}
        />
        <YAxis
          tickLine={false}
          axisLine={false}
          width={48}
          tickFormatter={v => (Number(v) === 0 ? '0' : `${Math.round(Number(v) / 10000)}万`)}
        />
        <ChartTooltip
          cursor={false}
          content={
            <ChartTooltipContent
              labelFormatter={(_, p) => `11/${p?.[0]?.payload?.day}`}
              formatter={v => (
                <span className="font-medium tabular-nums">{formatMoney(Number(v))}</span>
              )}
            />
          }
        />
        <Bar dataKey="sales" fill="var(--color-sales)" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ChartContainer>
  );
}

export function StoreStatusBadge({ status }: { status: 'ACTIVE' | 'PREPARING' }) {
  return status === 'ACTIVE' ? (
    <Badge variant="outline" className="border-transparent bg-success/10 text-success-strong">
      稼働中
    </Badge>
  ) : (
    <Badge variant="outline">準備中</Badge>
  );
}

/** 順位の行: 32px の primary tint の順位・名前・右端の金額（DESIGN.md）。 */
export function RankRow({
  rank,
  name,
  amount,
  extra,
}: {
  rank: number;
  name: string;
  amount: number;
  extra?: ReactNode;
}) {
  return (
    <Link
      href="#"
      className="flex items-center gap-3 px-6 py-3 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary"
    >
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary-strong tabular-nums">
        {rank}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm text-foreground" title={name}>
          {name}
        </span>
        {extra}
      </span>
      <span className="shrink-0 text-sm font-medium tabular-nums text-foreground">
        {formatMoney(amount)}
      </span>
    </Link>
  );
}

export function AsOf({ children }: { children: ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>;
}

/**
 * 最近の数件の一覧。失敗の「件数」は新しい集計が要る（spec の対象外）ので、行ごとの状態で見せる。
 */
export function RecentList({
  region,
  title,
  rows,
  failMessage,
}: {
  region: string;
  title: string;
  rows: { title: string; status: string; at: string }[];
  failMessage: string;
}) {
  return (
    <Card className="gap-0 py-0">
      <CardHeader className="py-4">
        <CardTitle role="heading" aria-level={2}>
          {title}
        </CardTitle>
      </CardHeader>
      <RegionBody
        region={region}
        failMessage={failMessage}
        skeleton={
          <div className="space-y-3 border-t px-6 py-4">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        }
      >
        <ul className="divide-y border-t">
          {rows.map(r => (
            <li key={r.title + r.at} className="flex items-center gap-2 px-6 py-3">
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm text-foreground" title={r.title}>
                  {r.title}
                </span>
                <span className="block text-xs text-muted-foreground">{r.at}</span>
              </span>
              {r.status === '失敗' ? (
                <Badge
                  variant="outline"
                  className="border-transparent bg-destructive/10 text-destructive-strong"
                >
                  失敗
                </Badge>
              ) : (
                <span className="shrink-0 text-xs text-muted-foreground">{r.status}</span>
              )}
            </li>
          ))}
        </ul>
        <Link
          href="#"
          className="block border-t px-6 py-3 text-sm text-primary-strong hover:bg-muted"
        >
          すべて見る
        </Link>
      </RegionBody>
    </Card>
  );
}
