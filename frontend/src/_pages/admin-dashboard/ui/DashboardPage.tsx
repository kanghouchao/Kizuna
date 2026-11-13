'use client';

import type { ReactNode } from 'react';
import { MePending, hasPermission, useMe } from '@/entities/user';
import { Card, CardContent } from '@/shared/ui';
import { SalesRankingRegion } from './SalesRankingRegion';
import { StoresRegion } from './StoresRegion';

/**
 * グループの文脈のダッシュボード。数字の区画は 1 枚の帳票カードに並べ、幅で折り返す（固定列は 8 桁の金額がはみ出る）。
 * 区画は区画の権限で出し分け、見られない区画は枠ごと出さない。
 */
export default function AdminDashboard() {
  const { me } = useMe();

  const numbers: ReactNode[] = [];
  if (me !== null && hasPermission(me, 'DASHBOARD_STORES_VIEW'))
    numbers.push(<StoresRegion key="stores" canOpenStores={hasPermission(me, 'STORE_MANAGE')} />);
  const ranking = me !== null && hasPermission(me, 'DASHBOARD_SALES_RANKING_VIEW');

  return (
    // 外殻（サイドバー・ヘッダー・幅制約・余白）は (admin) の layout が供給するため、
    // このページは中身だけを描く
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-foreground">グループ</h1>
      {me === null ? (
        <MePending />
      ) : numbers.length === 0 && !ranking ? (
        <NothingToShow />
      ) : (
        <>
          {numbers.length > 0 && (
            <Card>
              <CardContent>
                <div className="grid grid-cols-[repeat(auto-fit,minmax(15rem,1fr))] gap-x-8 gap-y-6">
                  {numbers}
                </div>
              </CardContent>
            </Card>
          )}
          {ranking && <SalesRankingRegion />}
        </>
      )}
    </div>
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
