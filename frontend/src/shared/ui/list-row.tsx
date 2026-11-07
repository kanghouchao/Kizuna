import type { ReactNode } from 'react';
import Link from 'next/link';
import { ChevronRightIcon } from 'lucide-react';
import { Skeleton } from './skeleton';

/** 本人ポータルの一覧。行の区切り線を持つカード 1 枚。 */
export function ListRows({ label, children }: { label?: string; children: ReactNode }) {
  return (
    <ul aria-label={label} className="divide-y rounded-xl border bg-card text-card-foreground">
      {children}
    </ul>
  );
}

interface ListRowProps {
  title: ReactNode;
  /** 題の下の補足（日付・店舗名など）。 */
  meta?: ReactNode;
  /** 右端の値（金額・状態ラベル）。 */
  trailing?: ReactNode;
  /** 渡すと行全体が詳細へのリンクになる。 */
  href?: string;
}

/** 一覧の行。押せる領域は会員の段（48px）を満たす高さを取る。hover の面では補足も foreground に切り替える。 */
export function ListRow({ title, meta, trailing, href }: ListRowProps) {
  const body = (
    <>
      <div className="min-w-0 flex-1">
        <div className="wrap-anywhere">{title}</div>
        {meta && (
          <div className="text-sm wrap-anywhere text-muted-foreground group-hover:text-foreground">
            {meta}
          </div>
        )}
      </div>
      {trailing && <div className="shrink-0 text-right">{trailing}</div>}
      {href && (
        <ChevronRightIcon
          className="size-5 shrink-0 text-muted-foreground group-hover:text-foreground"
          aria-hidden="true"
        />
      )}
    </>
  );
  const layout = 'flex min-h-12 items-center gap-3 px-4 py-3';
  return (
    <li>
      {href ? (
        <Link
          href={href}
          className={`${layout} group hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none focus-visible:ring-inset`}
        >
          {body}
        </Link>
      ) : (
        <div className={layout}>{body}</div>
      )}
    </li>
  );
}

/** 読み込み中の一覧。ListRow と同じ形の骨組みを rows 行出す。 */
export function ListRowsSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div role="status" aria-label="読み込み中" className="divide-y rounded-xl border bg-card">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex min-h-12 items-center gap-3 px-4 py-3">
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3 w-1/3" />
          </div>
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}
