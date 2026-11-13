'use client';

import Link from 'next/link';
import { formatMoney, storePath, useResource } from '@/shared/lib';
import {
  Badge,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  RegionError,
  Skeleton,
} from '@/shared/ui';
import { dashboardApi } from '../api/dashboard';

const TITLE_ID = 'dashboard-sales-ranking-title';

/** 店舗別の今月の売上の順位。全店を縦に並べ切り、各行に首位比の棒を添え、行からその店舗のダッシュボードへ進む。 */
export function SalesRankingRegion() {
  const { data, isLoading, failure, reload } = useResource(() => dashboardApi.getSalesRanking());

  return (
    <section aria-labelledby={TITLE_ID}>
      <Card className="gap-0 py-0">
        <CardHeader className="py-4">
          <CardTitle id={TITLE_ID} role="heading" aria-level={2}>
            店舗別の今月の売上
          </CardTitle>
        </CardHeader>
        {isLoading ? (
          <div className="space-y-3 border-t px-6 py-4">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-8 w-full" />
            ))}
          </div>
        ) : failure !== null || data === null ? (
          <CardContent className="border-t py-4">
            <RegionError message="店舗別の売上の取得に失敗しました" onRetry={() => void reload()} />
          </CardContent>
        ) : data.stores.length === 0 ? (
          <p className="border-t px-6 py-4 text-sm text-muted-foreground">担当店舗がありません。</p>
        ) : (
          <ol className="divide-y border-t">
            {data.stores.map((store, i) => (
              <li key={store.store_id}>
                <Link
                  href={storePath(store.store_id, '/dashboard')}
                  className="grid grid-cols-[2rem_minmax(0,1fr)_minmax(4rem,14rem)_7rem] items-center gap-3 px-6 py-3 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary"
                >
                  <span className="flex size-8 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary-strong tabular-nums">
                    {i + 1}
                  </span>
                  <span className="flex min-w-0 items-center gap-2">
                    <span className="truncate text-sm text-foreground" title={store.store_name}>
                      {store.store_name}
                    </span>
                    {store.store_status === 'PREPARING' && (
                      <Badge variant="outline" className="shrink-0">
                        準備中
                      </Badge>
                    )}
                  </span>
                  <span className="h-2 rounded-full bg-muted" aria-hidden>
                    <span
                      className="block h-2 rounded-full bg-primary-strong"
                      style={{ width: `${share(store.sales, data.stores[0].sales)}%` }}
                    />
                  </span>
                  <span className="text-right text-sm font-medium tabular-nums text-foreground">
                    {formatMoney(store.sales)}
                  </span>
                </Link>
              </li>
            ))}
          </ol>
        )}
      </Card>
    </section>
  );
}

// 首位も 0 円なら棒は全店 0 幅（0 で割らない）
function share(sales: number, top: number): number {
  return top === 0 ? 0 : (sales / top) * 100;
}
