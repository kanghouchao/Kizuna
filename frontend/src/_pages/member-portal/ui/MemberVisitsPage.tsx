'use client';

import { MemberVisit, memberVisitApi } from '@/entities/order';
import { tokyoDate, useListPage } from '@/shared/lib';
import { EmptyState, ListRowsSkeleton, Pager, RegionError } from '@/shared/ui';
import { MEMBER_PAGER } from './memberPager';
import { VisitRows } from './VisitRows';

/**
 * 会員ポータルの来店履歴のすべて。全店舗の来店を新しい順に並べる。
 *
 * 出るのは会計の済んだ来店だけで、申請中の予約は「予約」の画面が別に追う。店舗との関連を解除しても
 * 過去の来店は残る（来店は確定した記録であって、現在の関連の写しではない）。
 */
export function MemberVisitsPage() {
  const list = useListPage<MemberVisit>(page => memberVisitApi.list(page));
  const today = tokyoDate(new Date());

  return (
    <div className="space-y-4">
      {list.failed ? (
        <RegionError message="来店履歴を取得できませんでした" onRetry={() => void list.reload()} />
      ) : list.isLoading && list.rows.length === 0 ? (
        <ListRowsSkeleton rows={5} />
      ) : list.rows.length === 0 ? (
        <EmptyState message="まだ来店の記録はありません" />
      ) : (
        <VisitRows visits={list.rows} today={today} />
      )}
      <Pager
        className={MEMBER_PAGER}
        page={list.page}
        pageCount={list.pageCount}
        total={list.total}
        rowCount={list.rows.length}
        disabled={list.isLoading}
        onPageChange={page => void list.onPageChange(page)}
      />
    </div>
  );
}
