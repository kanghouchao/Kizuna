import { ChevronRightIcon } from 'lucide-react';
import Link from 'next/link';
import { Skeleton } from '@/shared/ui';

/** 区画の件数の一行。href は一覧を開ける者にだけ渡し、無ければ件数だけを出す。 */
export function CountRow({ label, count, href }: { label: string; count: number; href?: string }) {
  const body = (
    <>
      <span className="flex-1 text-sm text-foreground">{label}</span>
      <span className="text-lg font-bold tabular-nums text-foreground">{count}</span>
      <span className="text-sm text-muted-foreground">件</span>
    </>
  );
  if (href === undefined) return <div className="flex items-center gap-3 px-6 py-3">{body}</div>;
  return (
    <Link
      href={href}
      className="flex items-center gap-3 px-6 py-3 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary"
    >
      {body}
      <ChevronRightIcon className="size-4 text-muted-foreground" aria-hidden />
    </Link>
  );
}

export function CountRowSkeleton() {
  return (
    <div className="flex items-center gap-3 px-6 py-3">
      <Skeleton className="h-4 flex-1" />
      <Skeleton className="h-6 w-10" />
    </div>
  );
}
