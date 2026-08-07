'use client';

import { useEffect, useState, useCallback } from 'react';
import { StoreStats, platformStoreApi } from '@/entities/store';
import { Card, CardContent, RegionError, Skeleton } from '@/shared/ui';

export default function AdminDashboard() {
  const [stats, setStats] = useState<StoreStats | null>(null);
  const [loadingStats, setLoadingStats] = useState(true);

  // 再試行から呼び直せるよう effect の外に置く。先頭で読み込み中へ戻すのは、同じ姿のまま
  // 二度目の失敗を迎えると RegionError が mount し直されず読み上げに何も届かないため
  const loadStats = useCallback(async () => {
    setLoadingStats(true);
    try {
      setStats(await platformStoreApi.getStats());
    } catch {
      // 取れなかった統計を残すと 0 と表示され、店舗が 1 つも無い状態と見分けがつかない
      setStats(null);
    } finally {
      setLoadingStats(false);
    }
  }, []);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  return (
    // 外殻（サイドバー・ヘッダー・幅制約・余白）は (admin) の layout が供給するため、
    // このページは中身だけを描く
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      <Card>
        <CardContent>
          {loadingStats ? (
            <>
              <Skeleton className="h-4 w-3/4 mb-2" />
              <Skeleton className="h-8 w-1/2" />
            </>
          ) : !stats ? (
            // 読み込みを終えて統計が無いのは取得に失敗したときだけ
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
    </div>
  );
}
