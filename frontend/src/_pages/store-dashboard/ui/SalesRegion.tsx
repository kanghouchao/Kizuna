'use client';

import { ArrowDownRightIcon, ArrowUpRightIcon } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts';
import { cn, formatMoney, storePath, useResource } from '@/shared/lib';
import { Card, CardContent, CardHeader, CardTitle, RegionError, Skeleton } from '@/shared/ui';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/shared/ui/chart';
import { dashboardApi, type Sales } from '../api/dashboard';

const chartConfig = {
  sales: { label: '売上', color: 'var(--primary-strong)' },
} satisfies ChartConfig;

/**
 * 売上の区画。今日と今月の売上の統計カードと、今月の日別推移の棒グラフを一度の取得から描く。
 * 金額から運営集計への導線は、運営集計を開ける者（canOpenReports）にだけ出す。
 */
export function SalesRegion({
  storeId,
  canOpenReports,
}: {
  storeId: string;
  canOpenReports: boolean;
}) {
  const { data, isLoading, failure, reload } = useResource(() => dashboardApi.getSales());

  if (!isLoading && (failure !== null || data === null))
    return (
      <Card>
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            売上
          </CardTitle>
        </CardHeader>
        <CardContent>
          <RegionError message="売上の取得に失敗しました" onRetry={() => void reload()} />
        </CardContent>
      </Card>
    );

  const sales = isLoading ? null : data;
  const reportHref = canOpenReports ? storePath(storeId, '/operational-reports') : undefined;
  return (
    <>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(16rem,1fr))] gap-6">
        <Card>
          <CardContent>
            {sales === null ? (
              <StatSkeleton />
            ) : (
              <Stat
                note="今日の売上"
                amount={sales.today.sales}
                count={sales.today.order_count}
                href={reportHref}
              />
            )}
          </CardContent>
        </Card>
        <Card>
          <CardContent>
            {sales === null ? (
              <StatSkeleton />
            ) : (
              <Stat
                note="今月の売上"
                amount={sales.month_to_date.sales}
                count={sales.month_to_date.order_count}
                href={reportHref}
                delta={
                  <Delta
                    now={sales.month_to_date.sales}
                    before={sales.previous_month_to_date.sales}
                  />
                }
              />
            )}
          </CardContent>
        </Card>
      </div>
      <Card>
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            今月の日別の売上
          </CardTitle>
          {sales !== null && (
            <p className="text-sm text-muted-foreground">営業日 {sales.business_date} まで</p>
          )}
        </CardHeader>
        <CardContent>
          {sales === null ? <Skeleton className="h-64 w-full" /> : <DailyChart sales={sales} />}
        </CardContent>
      </Card>
    </>
  );
}

/** 統計カードの中身: 注釈・数値・増減の順（DESIGN.md）。 */
function Stat({
  note,
  amount,
  count,
  href,
  delta,
}: {
  note: string;
  amount: number;
  count: number;
  href?: string;
  delta?: ReactNode;
}) {
  return (
    <div className="min-w-0 space-y-1">
      <p className="text-sm text-muted-foreground">{note}</p>
      <p className="text-3xl font-bold tabular-nums text-foreground">
        {href === undefined ? (
          formatMoney(amount)
        ) : (
          <Link
            href={href}
            className="rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {formatMoney(amount)}
          </Link>
        )}
      </p>
      <p className="text-sm tabular-nums text-muted-foreground">完了 {count} 件</p>
      {delta}
    </div>
  );
}

/** 前月同時点との増減。前月同時点が 0 なら率は出せないので金額だけを並べる。 */
function Delta({ now, before }: { now: number; before: number }) {
  const label = <span className="text-muted-foreground">前月同時点 {formatMoney(before)}</span>;
  if (before === 0) return <p className="text-sm tabular-nums">{label}</p>;
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
      {label}
    </p>
  );
}

function StatSkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-4 w-24" />
      <Skeleton className="h-8 w-40" />
      <Skeleton className="h-4 w-32" />
    </div>
  );
}

function DailyChart({ sales }: { sales: Sales }) {
  return (
    <ChartContainer config={chartConfig} className="aspect-auto h-64 w-full">
      <BarChart data={sales.daily} margin={{ left: 4, right: 4, top: 8 }} barCategoryGap={2}>
        <CartesianGrid vertical={false} />
        <XAxis
          dataKey="business_date"
          tickLine={false}
          axisLine={false}
          tickMargin={8}
          tickFormatter={(date: string) => `${Number(date.slice(8))}日`}
        />
        <YAxis
          tickLine={false}
          axisLine={false}
          width={64}
          // 売上の無い月でも目盛が 0 だけに潰れないよう、上端を 1 万円以上に保つ
          domain={[0, (max: number) => Math.max(max, 10_000)]}
          tickFormatter={(value: number) =>
            value === 0
              ? '0'
              : `${(value / 10_000).toLocaleString('ja-JP', { maximumFractionDigits: 2 })}万`
          }
        />
        <ChartTooltip
          cursor={false}
          content={
            <ChartTooltipContent
              labelFormatter={(_, payload) => payload[0]?.payload?.business_date}
              formatter={value => (
                <span className="font-medium tabular-nums">{formatMoney(Number(value))}</span>
              )}
            />
          }
        />
        <Bar dataKey="sales" fill="var(--color-sales)" radius={[4, 4, 0, 0]} maxBarSize={40} />
      </BarChart>
    </ChartContainer>
  );
}
