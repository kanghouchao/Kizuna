import type { ReactNode } from 'react';
import { cn } from '@/shared/lib/utils';

/** キー値の明細書型（DESIGN.md「キー値と変更前後」）。行は KeyValueRow で並べる。本人ポータルは className で本文の大きさに合わせる。 */
export function KeyValueList({ children, className }: { children: ReactNode; className?: string }) {
  return <dl className={cn('divide-y text-sm', className)}>{children}</dl>;
}

interface KeyValueRowProps {
  label: ReactNode;
  children: ReactNode;
  /** 複数行になりうる値（理由・本文・備考）。ラベルの下に左寄せで置く。 */
  long?: boolean;
  /** 金額。桁を揃える。 */
  money?: boolean;
}

export function KeyValueRow({ label, children, long, money }: KeyValueRowProps) {
  const value = cn('min-w-0 wrap-anywhere', money && 'tabular-nums');
  if (long)
    return (
      <div data-layout="stacked" className="space-y-1 py-2">
        <dt className="text-muted-foreground">{label}</dt>
        <dd className={cn(value, 'whitespace-pre-wrap')}>{children}</dd>
      </div>
    );
  return (
    <div data-layout="inline" className="flex items-baseline justify-between gap-6 py-2">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd className={cn(value, 'text-right')}>{children}</dd>
    </div>
  );
}
