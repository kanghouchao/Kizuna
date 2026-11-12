'use client';

import type { ReactNode } from 'react';
import { MePending, hasPermission, useMe, useStoreContext } from '@/entities/user';
import { Badge } from '@/shared/ui';
import { TodayOrdersRegion } from './TodayOrdersRegion';

/**
 * 店舗の文脈のダッシュボード。左に数字、右の細い列に今日やることを置き、狭い幅では今日やることを先に積む。
 * 区画は区画の権限で出し分け、見られない区画は枠ごと出さない。シェルが URL の店舗を授権店舗に確かめてから描くので、店舗は必ず定まる。
 */
export default function StoreDashboardPage() {
  const { currentStore, currentStoreId } = useStoreContext();
  const { me } = useMe();
  if (currentStore === undefined || currentStoreId === undefined) return null;

  const numbers: ReactNode[] = [];
  const today: ReactNode[] = [];
  if (me !== null && hasPermission(me, 'DASHBOARD_TODAY_ORDERS_VIEW'))
    today.push(
      <TodayOrdersRegion
        key="today-orders"
        storeId={currentStoreId}
        canOpenOrders={hasPermission(me, 'ORDER_MANAGE')}
      />
    );

  return (
    <div className="space-y-6">
      <div className="flex min-w-0 items-center gap-3">
        <h1 className="truncate text-2xl font-bold text-foreground" title={currentStore.name}>
          {currentStore.name}
        </h1>
        {currentStore.status !== undefined && <StoreStatusBadge status={currentStore.status} />}
      </div>
      {me === null ? (
        <MePending />
      ) : numbers.length === 0 && today.length === 0 ? (
        <NothingToShow />
      ) : (
        <div className="@container">
          <div
            className={
              numbers.length > 0 && today.length > 0
                ? 'grid grid-cols-1 gap-6 @4xl:grid-cols-[minmax(0,1fr)_20rem]'
                : 'grid grid-cols-1 gap-6'
            }
          >
            {numbers.length > 0 && (
              <div className="order-2 min-w-0 space-y-6 @4xl:order-1">{numbers}</div>
            )}
            {today.length > 0 && (
              // 数字の列が無いときは、今日の区画を全幅の格子に並べ直す
              <div
                className={
                  numbers.length > 0
                    ? 'order-1 space-y-6 @4xl:order-2'
                    : 'grid grid-cols-[repeat(auto-fit,minmax(18rem,1fr))] items-start gap-6'
                }
              >
                {today}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/** 準備中は素の outline、稼働中は肯定側の tint（DESIGN.md のステータスピル）。 */
function StoreStatusBadge({ status }: { status: 'PREPARING' | 'ACTIVE' }) {
  return status === 'ACTIVE' ? (
    <Badge
      variant="outline"
      className="shrink-0 border-transparent bg-success/10 text-success-strong"
    >
      稼働中
    </Badge>
  ) : (
    <Badge variant="outline" className="shrink-0">
      準備中
    </Badge>
  );
}

function NothingToShow() {
  return (
    <div className="rounded-xl border p-8 text-center">
      <p className="text-sm text-foreground">このダッシュボードで表示できる項目がありません。</p>
      <p className="mt-1 text-sm text-muted-foreground">
        表示には管理者からの権限の付与が必要です。
      </p>
    </div>
  );
}
