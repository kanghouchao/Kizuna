'use client';

import { useResource } from '@/shared/lib';
import { RegionError, SalesStat, SalesStatSkeleton } from '@/shared/ui';
import { dashboardApi } from '../api/dashboard';

/**
 * 店舗集合の売上の区画。一度の取得から今日と今月の二つの統計を帳票カードの格子に直接並べる（枠はページが持つ）。
 * 増減は今月だけ。金額から横断の運営集計への導線は、それを開ける者（canOpenReports）にだけ出す。
 */
export function SalesRegion({ canOpenReports }: { canOpenReports: boolean }) {
  const { data, isLoading, failure, reload } = useResource(() => dashboardApi.getSales());

  if (isLoading)
    return (
      <>
        <SalesStatSkeleton />
        <SalesStatSkeleton />
      </>
    );
  if (failure !== null || data === null)
    // 取れなかった売上を ¥0 と出すと、売上の無い日と見分けがつかない
    return <RegionError message="売上の取得に失敗しました" onRetry={() => void reload()} />;

  const href = canOpenReports ? '/admin/operational-reports' : undefined;
  return (
    <>
      <SalesStat
        title="今日の売上"
        amount={data.today.sales}
        count={data.today.order_count}
        href={href}
      />
      <SalesStat
        title="今月の売上"
        amount={data.month_to_date.sales}
        count={data.month_to_date.order_count}
        href={href}
        previous={data.previous_month_to_date.sales}
      />
    </>
  );
}
