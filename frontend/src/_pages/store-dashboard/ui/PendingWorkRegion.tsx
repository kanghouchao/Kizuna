'use client';

import { useResource } from '@/shared/lib';
import { Card, CardHeader, CardTitle, RegionError } from '@/shared/ui';
import { CountRow, CountRowSkeleton } from './CountRow';

/** やることの一行。行ごとに区画の権限で出し分ける別の区画なので、取得・読み込み・失敗も行ごとに持つ。 */
export interface PendingWork {
  key: string;
  label: string;
  fetchCount: () => Promise<number>;
  /** 一覧を開ける者にだけ渡す。 */
  href?: string;
}

/** 未処理の件数を並べる「やること」のカード。見られる行が無ければページ側でカードごと出さない。 */
export function PendingWorkRegion({ rows }: { rows: PendingWork[] }) {
  return (
    <Card className="gap-0 py-0">
      <CardHeader className="py-4">
        <CardTitle role="heading" aria-level={2}>
          やること
        </CardTitle>
      </CardHeader>
      <ul className="divide-y border-t">
        {rows.map(row => (
          <li key={row.key}>
            <PendingWorkRow row={row} />
          </li>
        ))}
      </ul>
    </Card>
  );
}

function PendingWorkRow({ row }: { row: PendingWork }) {
  const { data, isLoading, failure, reload } = useResource(row.fetchCount);
  if (isLoading) return <CountRowSkeleton />;
  if (failure !== null || data === null)
    return (
      <RegionError
        className="px-6 py-3"
        message={`${row.label}の件数の取得に失敗しました`}
        onRetry={() => void reload()}
      />
    );
  return <CountRow label={row.label} count={data} href={row.href} />;
}
