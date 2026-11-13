'use client';

import { ORDER_STATUS_LABELS, type OrderStatus } from '@/entities/order';
import { storePath, useResource } from '@/shared/lib';
import { Card, CardHeader, CardTitle, RegionError } from '@/shared/ui';
import { dashboardApi, type TodayOrders } from '../api/dashboard';
import { CountRow, CountRowSkeleton } from './CountRow';

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
            <CountRowSkeleton key={row.status} />
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
