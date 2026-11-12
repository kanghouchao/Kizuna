'use client';

import { ChevronRightIcon } from 'lucide-react';
import Link from 'next/link';
import { ORDER_STATUS_LABELS, type OrderStatus } from '@/entities/order';
import { storePath, useResource } from '@/shared/lib';
import { Card, CardHeader, CardTitle, RegionError, Skeleton } from '@/shared/ui';
import { dashboardApi, type TodayOrders } from '../api/dashboard';

const ROWS: { status: OrderStatus; count: (orders: TodayOrders) => number }[] = [
  { status: 'CONFIRMED', count: orders => orders.confirmed },
  { status: 'IN_SERVICE', count: orders => orders.in_service },
  { status: 'COMPLETED', count: orders => orders.completed },
  { status: 'CANCELLED', count: orders => orders.cancelled },
];

/** 今日の受注の区画。行から受注の一覧への導線は、一覧を開ける者（canOpenOrders）にだけ出す。 */
export function TodayOrdersRegion({
  storeId,
  canOpenOrders,
}: {
  storeId: string;
  canOpenOrders: boolean;
}) {
  const { data, isLoading, failure, reload } = useResource(() => dashboardApi.getTodayOrders());

  return (
    <Card className="gap-0 py-0">
      <CardHeader className="py-4">
        <CardTitle role="heading" aria-level={2}>
          今日の受注
        </CardTitle>
        {data !== null && !isLoading && failure === null && (
          <p className="text-sm text-muted-foreground">営業日 {data.business_date}</p>
        )}
      </CardHeader>
      {isLoading ? (
        <div className="divide-y border-t">
          {ROWS.map(row => (
            <div key={row.status} className="flex items-center gap-3 px-6 py-3">
              <Skeleton className="h-4 flex-1" />
              <Skeleton className="h-6 w-10" />
            </div>
          ))}
        </div>
      ) : failure !== null || data === null ? (
        <RegionError
          className="mx-6 mb-4"
          message="今日の受注件数の取得に失敗しました"
          onRetry={() => void reload()}
        />
      ) : (
        <ul className="divide-y border-t">
          {ROWS.map(row => (
            <li key={row.status}>
              <CountRow
                label={ORDER_STATUS_LABELS[row.status]}
                count={row.count(data)}
                href={
                  canOpenOrders
                    ? storePath(storeId, `/orders?business_date=${data.business_date}`)
                    : undefined
                }
              />
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function CountRow({ label, count, href }: { label: string; count: number; href?: string }) {
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
