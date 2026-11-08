'use client';

import { memberPointApi } from '@/entities/point';
import { formatPoints, useResource } from '@/shared/lib';
import { RegionError, Skeleton } from '@/shared/ui';

/** 現在の残高の区画。ポイントの画面と伝票の申領後が同じ区画を出す。取得はマウント時。 */
export function PointBalance() {
  const balance = useResource(() => memberPointApi.balance());
  return (
    <section
      aria-labelledby="point-balance"
      className="space-y-1 rounded-xl border bg-card p-4 text-card-foreground"
    >
      <h2 id="point-balance" className="text-muted-foreground">
        現在の残高
      </h2>
      {balance.failure ? (
        <RegionError message="残高を取得できませんでした" onRetry={() => void balance.reload()} />
      ) : balance.isLoading || balance.data === null ? (
        <Skeleton role="status" aria-label="読み込み中" className="h-10 w-48" />
      ) : (
        <p className="text-3xl font-bold tabular-nums">{formatPoints(balance.data.balance)}</p>
      )}
    </section>
  );
}
