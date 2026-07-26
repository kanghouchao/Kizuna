'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAuth } from '@/entities/user';
import { useRouter } from 'next/navigation';
import { Store, StoreStats, platformStoreApi } from '@/entities/store';
import { Badge, Button, Card, CardContent, Skeleton } from '@/shared/ui';
import toast from 'react-hot-toast';

export default function AdminDashboard() {
  const { logout } = useAuth();
  const router = useRouter();
  const [stats, setStats] = useState<StoreStats | null>(null);
  const [recentStores, setRecentStores] = useState<Store[]>([]);
  const [loadingStats, setLoadingStats] = useState(true);

  const loadDashboardData = useCallback(async () => {
    try {
      const [statsResponse, storesResponse] = await Promise.all([
        platformStoreApi.getStats(),
        platformStoreApi.getList({ per_page: 5, page: 1 }),
      ]);

      setStats(statsResponse);
      setRecentStores(storesResponse.data);
    } catch (error) {
      toast.error('データの読み込みに失敗しました');
    } finally {
      setLoadingStats(false);
    }
  }, []);

  useEffect(() => {
    loadDashboardData();
  }, [loadDashboardData]);

  return (
    <div className="min-h-screen bg-background">
      {/* ナビゲーションバー */}
      <nav className="bg-card shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <h1 className="text-xl font-semibold text-foreground">管理コンソール</h1>
            </div>
            <div className="flex items-center space-x-4">
              <span className="text-sm text-muted-foreground">ようこそ、adminさん</span>
              <Button onClick={() => router.push('/platform/stores')}>店舗管理</Button>
              <Button variant="ghost" size="sm" onClick={logout}>
                ログアウト
              </Button>
            </div>
          </div>
        </div>
      </nav>

      {/* メインコンテンツ */}
      <div className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            {loadingStats ? (
              // 統計データ読み込み状態
              Array.from({ length: 4 }).map((_, i) => (
                <Card key={i}>
                  <CardContent>
                    <Skeleton className="h-4 w-3/4 mb-2" />
                    <Skeleton className="h-8 w-1/2" />
                  </CardContent>
                </Card>
              ))
            ) : (
              // 統計データカード
              <>
                <Card>
                  <CardContent>
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
                          <dt className="text-sm font-medium text-muted-foreground truncate">
                            総店舗数
                          </dt>
                          <dd className="text-lg font-medium text-foreground">
                            {stats?.total || 0}
                          </dd>
                        </dl>
                      </div>
                    </div>
                  </CardContent>
                </Card>

                <Card>
                  <CardContent>
                    <div className="flex items-center">
                      <div className="shrink-0">
                        <svg
                          className="h-6 w-6 text-success"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                          />
                        </svg>
                      </div>
                      <div className="ml-5 w-0 flex-1">
                        <dl>
                          <dt className="text-sm font-medium text-muted-foreground truncate">
                            有効店舗
                          </dt>
                          <dd className="text-lg font-medium text-foreground">
                            {stats?.active || 0}
                          </dd>
                        </dl>
                      </div>
                    </div>
                  </CardContent>
                </Card>

                <Card>
                  <CardContent>
                    <div className="flex items-center">
                      <div className="shrink-0">
                        <svg
                          className="h-6 w-6 text-warning"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"
                          />
                        </svg>
                      </div>
                      <div className="ml-5 w-0 flex-1">
                        <dl>
                          <dt className="text-sm font-medium text-muted-foreground truncate">
                            審査待ち
                          </dt>
                          <dd className="text-lg font-medium text-foreground">
                            {stats?.pending || 0}
                          </dd>
                        </dl>
                      </div>
                    </div>
                  </CardContent>
                </Card>

                <Card>
                  <CardContent>
                    <div className="flex items-center">
                      <div className="shrink-0">
                        <svg
                          className="h-6 w-6 text-destructive"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
                          />
                        </svg>
                      </div>
                      <div className="ml-5 w-0 flex-1">
                        <dl>
                          <dt className="text-sm font-medium text-muted-foreground truncate">
                            無効店舗
                          </dt>
                          <dd className="text-lg font-medium text-foreground">
                            {stats?.inactive || 0}
                          </dd>
                        </dl>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </>
            )}
          </div>

          {/* 直近追加店舗一覧 */}
          <Card className="py-0 overflow-hidden">
            <div className="px-4 py-5 sm:px-6 flex justify-between items-center border-b">
              <div>
                <h3 className="text-lg leading-6 font-medium text-foreground">
                  最近追加された店舗
                </h3>
                <p className="mt-1 max-w-2xl text-sm text-muted-foreground">直近で作成された5件</p>
              </div>
              <Button onClick={() => router.push('/platform/stores/create')}>店舗追加</Button>
            </div>
            <ul className="divide-y">
              {recentStores.length === 0 ? (
                <li className="px-4 py-4 text-center text-muted-foreground">
                  店舗データがありません
                </li>
              ) : (
                recentStores.map(store => (
                  <li key={store.id} className="px-4 py-4 hover:bg-muted">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center">
                        <div className="shrink-0 h-10 w-10">
                          <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center">
                            <span className="text-sm font-medium text-primary-strong">
                              {store.name.charAt(0).toUpperCase()}
                            </span>
                          </div>
                        </div>
                        <div className="ml-4">
                          <div className="text-sm font-medium text-foreground">{store.name}</div>
                          <div className="text-sm text-muted-foreground">{store.domain}</div>
                        </div>
                      </div>
                      <div className="flex items-center space-x-3">
                        <Badge
                          variant="outline"
                          className={`border-transparent ${
                            store.is_active
                              ? 'bg-success/10 text-success-strong'
                              : 'bg-destructive/10 text-destructive-strong'
                          }`}
                        >
                          {store.is_active ? '有効' : '無効'}
                        </Badge>
                        <span className="text-sm text-muted-foreground">
                          {new Date(store.created_at).toLocaleDateString('ja-JP')}
                        </span>
                      </div>
                    </div>
                  </li>
                ))
              )}
            </ul>
          </Card>

          {/* クイック操作 */}
          <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6">
            <Card>
              <CardContent>
                <div className="flex items-center">
                  <div className="shrink-0 rounded-lg bg-chart-1/10 p-3">
                    <svg
                      className="h-8 w-8 text-foreground"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M12 6v6m0 0v6m0-6h6m-6 0H6"
                      />
                    </svg>
                  </div>
                  <div className="ml-4">
                    <h3 className="text-lg font-medium text-foreground">店舗作成</h3>
                    <p className="text-sm text-muted-foreground">新しい店舗を追加</p>
                  </div>
                </div>
                <div className="mt-4">
                  <Button className="w-full" onClick={() => router.push('/platform/stores/create')}>
                    今すぐ作成
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <div className="flex items-center">
                  <div className="shrink-0 rounded-lg bg-chart-2/10 p-3">
                    <svg
                      className="h-8 w-8 text-foreground"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M9 5H7a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                      />
                    </svg>
                  </div>
                  <div className="ml-4">
                    <h3 className="text-lg font-medium text-foreground">店舗管理</h3>
                    <p className="text-sm text-muted-foreground">既存店舗の閲覧と編集</p>
                  </div>
                </div>
                <div className="mt-4">
                  <Button className="w-full" onClick={() => router.push('/platform/stores')}>
                    すべて表示
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <div className="flex items-center">
                  <div className="shrink-0 rounded-lg bg-chart-5/10 p-3">
                    <svg
                      className="h-8 w-8 text-foreground"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
                      />
                    </svg>
                  </div>
                  <div className="ml-4">
                    <h3 className="text-lg font-medium text-foreground">システム統計</h3>
                    <p className="text-sm text-muted-foreground">詳細レポートを表示</p>
                  </div>
                </div>
                <div className="mt-4">
                  <Button className="w-full" onClick={() => toast('機能開発中...', { icon: 'ℹ️' })}>
                    レポートを見る
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
