'use client';

import { useRef, useState } from 'react';
import { PlusIcon } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { Store, platformStoreApi } from '@/entities/store';
import { useListPage } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import { Button, ConfirmDialog, Input } from '@/shared/ui';
import toast from 'react-hot-toast';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 10;

export default function StoresPage() {
  const router = useRouter();
  const [searchTerm, setSearchTerm] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<Store | null>(null);
  // 適用済みの検索語。入力の state をそのまま読むと、値を変えた同一ハンドラ内で再取得したとき
  // 再レンダー前の古い値で取得してしまう（useListPage の search 制約）ため ref で持つ。
  const appliedSearch = useRef('');

  const list = useListPage(
    page =>
      platformStoreApi.getList({
        page,
        size: PAGE_SIZE,
        search: appliedSearch.current || undefined,
      }),
    '店舗一覧の読み込みに失敗しました'
  );
  const stores = list.rows;

  /** 検索語を適用して 1 ページ目から取り直す */
  const applySearch = (term: string) => {
    appliedSearch.current = term;
    list.search();
  };

  const handleDeleteStore = async () => {
    if (!deleteTarget) return;
    try {
      await platformStoreApi.delete(deleteTarget.id);
      toast.success('店舗を削除しました');
      void list.reload();
    } catch {
      toast.error('店舗の削除に失敗しました');
    }
  };

  return (
    <>
      <ListPage
        title="店舗一覧"
        description="システム内の全ての店舗を管理します"
        actions={
          <Button onClick={() => router.push('/platform/stores/create')}>
            <PlusIcon />
            店舗を追加
          </Button>
        }
        search={{
          onSearch: () => applySearch(searchTerm),
          content: (
            <>
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
                    applySearch('');
                  }}
                >
                  クリア
                </Button>
              )}
            </>
          ),
        }}
        state={list}
        emptyMessage={
          <>
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
          </>
        }
      >
        <ul className="divide-y">
          {stores.map(store => (
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
                    <p className="text-lg font-medium text-foreground truncate">{store.name}</p>
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
                    <Button variant="destructive" size="sm" onClick={() => setDeleteTarget(store)}>
                      削除
                    </Button>
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>
      </ListPage>

      {/* ダイアログは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <ConfirmDialog
        open={deleteTarget !== null}
        title={deleteTarget ? `店舗「${deleteTarget.name}」を削除しますか？` : ''}
        description="この操作は取り消せません。"
        onConfirm={() => void handleDeleteStore()}
        onClose={() => setDeleteTarget(null)}
      />
    </>
  );
}
