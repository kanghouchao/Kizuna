'use client';

import Link from 'next/link';
import { useResource } from '@/shared/lib';
import { Badge, RegionError, Skeleton } from '@/shared/ui';
import { dashboardApi } from '../api/dashboard';

const TITLE_ID = 'dashboard-stores-title';

/** 担当店舗の区画。帳票カードの一区切りとして描き、枠はページが持つ。店舗数から店舗一覧への導線は一覧を開ける者にだけ出す。 */
export function StoresRegion({ canOpenStores }: { canOpenStores: boolean }) {
  const { data, isLoading, failure, reload } = useResource(() => dashboardApi.getStores());

  return (
    <section aria-labelledby={TITLE_ID} className="min-w-0 space-y-1">
      <h2 id={TITLE_ID} className="text-sm font-normal text-muted-foreground">
        担当店舗
      </h2>
      {isLoading ? (
        <div className="space-y-3 pt-1">
          <Skeleton className="h-8 w-32" />
          <Skeleton className="h-5 w-40" />
        </div>
      ) : failure !== null || data === null ? (
        // 取れなかった数を 0 と出すと、担当店舗が 1 つも無い状態と見分けがつかない
        <RegionError message="担当店舗の取得に失敗しました" onRetry={() => void reload()} />
      ) : (
        <>
          <p className="text-3xl font-bold tabular-nums text-foreground">
            {canOpenStores ? (
              <Link
                href="/admin/stores"
                className="rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                {data.total} 店舗
              </Link>
            ) : (
              <>{data.total} 店舗</>
            )}
          </p>
          <p className="flex flex-wrap gap-2 text-sm">
            <Badge
              variant="outline"
              className="border-transparent bg-success/10 text-success-strong tabular-nums"
            >
              稼働中 {data.active}
            </Badge>
            <Badge variant="outline" className="tabular-nums">
              準備中 {data.preparing}
            </Badge>
          </p>
        </>
      )}
    </section>
  );
}
