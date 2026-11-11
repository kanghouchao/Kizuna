'use client';

import { useStoreContext } from '@/entities/user';
import { Badge, Card, CardContent } from '@/shared/ui';

/** 店舗の文脈のダッシュボード。シェルが URL の店舗を授権店舗に確かめてから描くので、店舗は必ず定まる。 */
export default function StoreDashboardPage() {
  const { currentStore } = useStoreContext();
  if (currentStore === undefined) return null;

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      <Card>
        <CardContent>
          <dl>
            <dt className="text-sm font-medium text-muted-foreground">店舗</dt>
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
        </CardContent>
      </Card>
    </div>
  );
}
