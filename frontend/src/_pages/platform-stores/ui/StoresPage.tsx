'use client';

import { useEffect, useState, useCallback } from 'react';
import { ChevronLeftIcon, ChevronRightIcon, PlusIcon } from '@heroicons/react/24/outline';
import { useRouter } from 'next/navigation';
import { Store, platformStoreApi } from '@/entities/store';
import { PaginatedResponse } from '@/shared/api';
import { Badge, Button, Card, CardContent, ConfirmDialog, Input } from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';
import toast from 'react-hot-toast';

export default function StoresPage() {
  const router = useRouter();
  const [stores, setStores] = useState<PaginatedResponse<Store> | null>(null);
  const [loadingStores, setLoadingStores] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [deleteTarget, setDeleteTarget] = useState<Store | null>(null);

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

  const handleDeleteStore = async () => {
    if (!deleteTarget) return;
    try {
      await platformStoreApi.delete(deleteTarget.id);
      toast.success('店舗を削除しました');
      loadStores();
    } catch (error) {
      toast.error('店舗の削除に失敗しました');
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="店舗一覧"
        description="システム内の全ての店舗を管理します"
        actions={
          <Button onClick={() => router.push('/platform/stores/create')}>
            <PlusIcon />
            店舗を追加
          </Button>
        }
      />

      {/* 検索フォーム。Enter での送信を残すため form がカードを包む */}
      <form onSubmit={handleSearch}>
        <Card>
          <CardContent className="flex flex-col md:flex-row md:items-center gap-4">
            <div className="w-full md:max-w-xs">
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
          </CardContent>
        </Card>
      </form>

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
                          onClick={() => setDeleteTarget(store)}
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

      <ConfirmDialog
        open={deleteTarget !== null}
        title={deleteTarget ? `店舗「${deleteTarget.name}」を削除しますか？` : ''}
        description="この操作は取り消せません。"
        onConfirm={() => void handleDeleteStore()}
        onClose={() => setDeleteTarget(null)}
      />
    </div>
  );
}
