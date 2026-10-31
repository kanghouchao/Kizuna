'use client';

import { adminStoreApi } from '@/entities/store';
import { MePending, hasPermission, useMe, useStoreContext } from '@/entities/user';
import { useResource } from '@/shared/lib';
import { Badge, Card, CardContent, RegionError, Skeleton } from '@/shared/ui';

/**
 * 全従業員の着地先。グループ全体の集計は店舗管理の権限を持つ者にだけ出し、
 * それ以外には現在店舗とその稼働状態を出す。
 */
export default function AdminDashboard() {
  const { me } = useMe();
  if (me === null) return <MePending />;
  const canManageStores = hasPermission(me, 'STORE_MANAGE');

  return (
    // 外殻（サイドバー・ヘッダー・幅制約・余白）は (admin) の layout が供給するため、
    // このページは中身だけを描く
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      {canManageStores ? <StoreStatsCard /> : <CurrentStoreCard />}
    </div>
  );
}

function CurrentStoreCard() {
  const { stores, currentStore, loadFailed, reload } = useStoreContext();

  return (
    <Card>
      <CardContent>
        {loadFailed && stores === null ? (
          <RegionError message="店舗情報の取得に失敗しました" onRetry={reload} />
        ) : stores === null ? (
          <>
            <Skeleton className="h-4 w-3/4 mb-2" />
            <Skeleton className="h-8 w-1/2" />
          </>
        ) : currentStore === undefined ? (
          <p className="text-sm text-muted-foreground">アクセス可能な店舗がありません</p>
        ) : (
          <dl>
            <dt className="text-sm font-medium text-muted-foreground">現在の店舗</dt>
            <dd className="mt-1 flex items-center gap-2">
              <span className="truncate text-lg font-medium text-foreground">
                {currentStore.name}
              </span>
              {/* 準備中は素の outline、稼働中は肯定側の tint（DESIGN.md のステータスピル） */}
              {currentStore.status === 'PREPARING' && <Badge variant="outline">準備中</Badge>}
              {currentStore.status === 'ACTIVE' && (
                <Badge
                  variant="outline"
                  className="border-transparent bg-success/10 text-success-strong"
                >
                  稼働中
                </Badge>
              )}
            </dd>
          </dl>
        )}
      </CardContent>
    </Card>
  );
}

function StoreStatsCard() {
  const {
    data: stats,
    isLoading: loadingStats,
    failure,
    reload: loadStats,
  } = useResource(() => adminStoreApi.getStats());

  return (
    <Card>
      <CardContent>
        {loadingStats ? (
          <>
            <Skeleton className="h-4 w-3/4 mb-2" />
            <Skeleton className="h-8 w-1/2" />
          </>
        ) : failure !== null ? (
          // 取れなかった統計を残すと 0 と表示され、店舗が 1 つも無い状態と見分けがつかない
          <RegionError message="店舗数の取得に失敗しました" onRetry={() => void loadStats()} />
        ) : (
          <div className="flex items-center">
            <div className="shrink-0">
              <svg
                className="h-6 w-6 text-muted-foreground"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
                />
              </svg>
            </div>
            <div className="ml-5 w-0 flex-1">
              <dl>
                <dt className="text-sm font-medium text-muted-foreground truncate">総店舗数</dt>
                <dd className="text-lg font-medium text-foreground">{stats?.total || 0}</dd>
              </dl>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
