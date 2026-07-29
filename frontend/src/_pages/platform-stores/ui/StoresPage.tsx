'use client';

import { useRef, useState } from 'react';
import { ExternalLinkIcon, PlusIcon, SquarePenIcon, StoreIcon, Trash2Icon } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { Store, isStoreDomain, platformStoreApi } from '@/entities/store';
import { useListPage } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Button,
  ConfirmDialog,
  Input,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
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
            <StoreIcon className="mx-auto h-12 w-12 text-muted-foreground" />
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
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>店舗名</TableHead>
              <TableHead>ドメイン</TableHead>
              <TableHead>登録日</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {stores.map(store => (
              <TableRow key={store.id}>
                <TableCell className="font-medium text-foreground">{store.name}</TableCell>
                <TableCell>
                  {/* 店舗サイトは店舗ドメインの別ホストで配信されるため、
                      現在のスキームを引き継ぐプロトコル相対 URL で別タブへ開く。
                      ホスト名の形でない値はリンクにしない（`正規.example@攻撃者.example` の
                      ような行は前半が userinfo と解釈され、別ホストへの導線になる） */}
                  {isStoreDomain(store.domain) ? (
                    <a
                      href={`//${store.domain}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 text-primary-strong hover:underline"
                    >
                      {store.domain}
                      <ExternalLinkIcon className="h-3.5 w-3.5" />
                    </a>
                  ) : (
                    <span className="text-muted-foreground">{store.domain}</span>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {new Date(store.created_at).toLocaleDateString('ja-JP')}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="編集"
                      onClick={() => router.push(`/platform/stores/${store.id}/edit`)}
                    >
                      <SquarePenIcon />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="削除"
                      onClick={() => setDeleteTarget(store)}
                    >
                      <Trash2Icon />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
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
