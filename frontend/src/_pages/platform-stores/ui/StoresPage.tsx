'use client';

import { useEffect, useState, useCallback } from 'react';
import { ChevronLeftIcon, ChevronRightIcon, PlusIcon } from '@heroicons/react/24/outline';
import { useAuth } from '@/entities/user';
import { useRouter } from 'next/navigation';
import { Store, platformStoreApi } from '@/entities/store';
import { PaginatedResponse } from '@/shared/api';
import { Badge, Button, Card, CardContent, Input } from '@/shared/ui';
import toast from 'react-hot-toast';

export default function StoresPage() {
  const { logout } = useAuth();
  const router = useRouter();
  const [stores, setStores] = useState<PaginatedResponse<Store> | null>(null);
  const [loadingStores, setLoadingStores] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);

  const loadStores = useCallback(async () => {
    setLoadingStores(true);
    try {
      const stores = await platformStoreApi.getList({
        page: currentPage,
        per_page: 10,
        search: searchTerm || undefined,
      });

      setStores(stores);
    } catch (error) {
      toast.error('店舗一覧の読み込みに失敗しました');
    } finally {
      setLoadingStores(false);
    }
  }, [currentPage, searchTerm]);

  useEffect(() => {
    loadStores();
  }, [loadStores, router]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setCurrentPage(1);
    loadStores();
  };

  const handleDeleteStore = async (id: string, name: string) => {
    if (!confirm(`店舗「${name}」を削除しますか？この操作は取り消せません。`)) {
      return;
    }

    try {
      await platformStoreApi.delete(id);
      toast.success('店舗を削除しました');
      loadStores();
    } catch (error) {
      toast.error('店舗の削除に失敗しました');
    }
  };

  return (
    <div className="min-h-screen bg-background">
      {/* ナビゲーションバー */}
      <nav className="bg-card shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center space-x-4">
              <Button
                variant="ghost"
                size="sm"
                className="text-primary-strong"
                onClick={() => router.push('/')}
              >
                ← ダッシュボードへ戻る
              </Button>
              <h1 className="text-xl font-semibold text-foreground">店舗管理</h1>
            </div>
            <div className="flex items-center space-x-4">
              <span className="text-sm text-muted-foreground">ようこそ、someone さん</span>
              <Button variant="ghost" size="sm" onClick={logout}>
                ログアウト
              </Button>
            </div>
          </div>
        </div>
      </nav>

      {/* 主要内容 */}
      <div className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          {/* ページヘッダー */}
          <div className="md:flex md:items-center md:justify-between mb-6">
            <div className="flex-1 min-w-0">
              <h2 className="text-2xl font-bold leading-7 text-foreground sm:text-3xl sm:truncate">
                店舗一覧
              </h2>
              <p className="mt-1 text-sm text-muted-foreground">
                システム内の全ての店舗を管理します
              </p>
            </div>
            <div className="mt-4 flex md:mt-0 md:ml-4">
              <Button onClick={() => router.push('/platform/stores/create')}>
                <PlusIcon />
                店舗を追加
              </Button>
            </div>
          </div>

          {/* 検索フォーム */}
          <Card className="mb-6">
            <CardContent>
              <form
                onSubmit={handleSearch}
                className="flex flex-col gap-3 sm:flex-row sm:items-center"
              >
                <div className="w-full sm:max-w-xs">
                  <label htmlFor="search" className="sr-only">
                    店舗を検索
                  </label>
                  <Input
                    type="text"
                    name="search"
                    id="search"
                    value={searchTerm}
                    onChange={e => setSearchTerm(e.target.value)}
                    placeholder="店舗名またはドメインで検索..."
                  />
                </div>
                <Button type="submit">検索</Button>
                {searchTerm && (
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      setSearchTerm('');
                      setCurrentPage(1);
                    }}
                  >
                    クリア
                  </Button>
                )}
              </form>
            </CardContent>
          </Card>

          {/* 店舗一覧 */}
          <Card className="py-0 overflow-hidden">
            {loadingStores ? (
              <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
            ) : stores && stores.data.length > 0 ? (
              <>
                <ul className="divide-y">
                  {stores.data.map(store => (
                    <li key={store.id} className="px-4 py-4">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center min-w-0 flex-1">
                          <div className="flex-shrink-0">
                            <div className="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center">
                              <span className="text-lg font-medium text-primary-strong">
                                {store.name.charAt(0).toUpperCase()}
                              </span>
                            </div>
                          </div>
                          <div className="ml-4 min-w-0 flex-1">
                            <div className="flex items-center">
                              <p className="text-lg font-medium text-foreground truncate">
                                {store.name}
                              </p>
                              <Badge
                                variant="outline"
                                className={`ml-2 border-transparent ${
                                  store.is_active
                                    ? 'bg-success/10 text-success-strong'
                                    : 'bg-destructive/10 text-destructive-strong'
                                }`}
                              >
                                {store.is_active ? '有効' : '無効'}
                              </Badge>
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center space-x-2">
                          <span className="text-sm text-muted-foreground">
                            {new Date(store.created_at).toLocaleDateString('ja-JP')}
                          </span>
                          <div className="flex space-x-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => router.push(`/platform/stores/${store.id}/edit`)}
                            >
                              編集
                            </Button>
                            <Button
                              variant="destructive"
                              size="sm"
                              onClick={() => handleDeleteStore(store.id, store.name)}
                            >
                              削除
                            </Button>
                          </div>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>

                {/* ページネーション */}
                {stores.last_page > 1 && (
                  <div className="px-4 py-3 flex items-center justify-between border-t sm:px-6">
                    <div className="flex-1 flex justify-between sm:hidden">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setCurrentPage(currentPage - 1)}
                        disabled={currentPage <= 1}
                      >
                        前へ
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="ml-3"
                        onClick={() => setCurrentPage(currentPage + 1)}
                        disabled={currentPage >= stores.last_page}
                      >
                        次へ
                      </Button>
                    </div>
                    <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
                      <div>
                        <p className="text-sm text-muted-foreground">
                          {stores.total} 件中 {stores.from}-{stores.to} を表示
                        </p>
                      </div>
                      <div>
                        <nav className="flex gap-1">
                          <Button
                            variant="outline"
                            size="icon-sm"
                            onClick={() => setCurrentPage(currentPage - 1)}
                            disabled={currentPage <= 1}
                          >
                            <ChevronLeftIcon />
                          </Button>

                          {/* ページ番号ボタン */}
                          {Array.from({ length: Math.min(5, stores.last_page) }, (_, i) => {
                            const page = i + 1;
                            return (
                              <Button
                                key={page}
                                variant="outline"
                                size="sm"
                                className={
                                  page === currentPage
                                    ? 'border-primary bg-primary/10 text-primary-strong'
                                    : undefined
                                }
                                onClick={() => setCurrentPage(page)}
                              >
                                {page}
                              </Button>
                            );
                          })}

                          <Button
                            variant="outline"
                            size="icon-sm"
                            onClick={() => setCurrentPage(currentPage + 1)}
                            disabled={currentPage >= stores.last_page}
                          >
                            <ChevronRightIcon />
                          </Button>
                        </nav>
                      </div>
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="px-4 py-12 text-center">
                <svg
                  className="mx-auto h-12 w-12 text-muted-foreground"
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
                <h3 className="mt-2 text-sm font-medium text-foreground">店舗がありません</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  {searchTerm ? '該当する店舗が見つかりません' : '最初の店舗を作成しましょう'}
                </p>
                <div className="mt-6">
                  <Button onClick={() => router.push('/platform/stores/create')}>
                    <PlusIcon />
                    店舗を追加
                  </Button>
                </div>
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
