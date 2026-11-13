import { ArrowDownRightIcon, ArrowUpRightIcon } from 'lucide-react';
import Link from 'next/link';
import { useId } from 'react';
import { formatMoney } from '@/shared/lib/portal-format';
import { cn } from '@/shared/lib/utils';
import { Skeleton } from './skeleton';

interface SalesStatProps {
  title: string;
  amount: number;
  count: number;
  /** 渡すと金額を運営集計などへの導線にする。 */
  href?: string;
  /** 渡すと前期（前月同時点）との増減を添える。 */
  previous?: number;
}

/** 売上の統計: 注釈・数値・件数・増減の順（DESIGN.md）。見出しを持つ区画として描き、枠は呼び出し側が持つ。 */
export function SalesStat({ title, amount, count, href, previous }: SalesStatProps) {
  const titleId = useId();
  return (
    <section aria-labelledby={titleId} className="min-w-0 space-y-1">
      <h2 id={titleId} className="text-sm font-normal text-muted-foreground">
        {title}
      </h2>
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
      <p className="text-sm tabular-nums text-muted-foreground">
        完了 {count.toLocaleString('ja-JP')} 件
      </p>
      {previous !== undefined && <Delta now={amount} before={previous} />}
    </section>
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

export function SalesStatSkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-4 w-24" />
      <Skeleton className="h-8 w-40" />
      <Skeleton className="h-4 w-32" />
    </div>
  );
}
