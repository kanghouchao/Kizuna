'use client';

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';
import {
  PlatformStore,
  ServiceIdentityResponse,
  ServiceIdentitySummaryResponse,
  platformAuthApi,
  serviceIdentityApi,
} from '@/entities/user';
import {
  ServiceIdentityCreateModal,
  ServiceIdentityEditModal,
  roleSetLabel,
  storeSetLabel,
} from '@/features/staff-management';
import { getApiErrorMessage, useDeleteAction, useListPage, useManagedList } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
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

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 10;

/**
 * サービスID管理ページ。対話ログインできない実行主体（バッチ・外部連携）の一覧と、
 * 一覧内モーダルでの新規作成・授権編集、停止・再開を行う。人のアカウント管理とは別の面。
 */
export default function ServiceIdentitiesPage() {
  const [searchTerm, setSearchTerm] = useState('');

  const list = useListPage<ServiceIdentitySummaryResponse, string>(
    (page, search) =>
      serviceIdentityApi.list({ page, size: PAGE_SIZE, search: search || undefined }),
    ''
  );
  const identities = list.rows;
  // 店舗目録はページで 1 回だけ取得し、一覧の対象範囲表示とモーダルの選択肢で共有する
  const {
    items: stores,
    isLoading: storesLoading,
    failed: storesFailed,
    refetch: refetchStores,
  } = useManagedList<PlatformStore>(() => platformAuthApi.stores());

  const [createOpen, setCreateOpen] = useState(false);
  // 編集対象は一覧から独立して保持する。分頁後の現在ページから導出すると、409 の再取得で
  // 対象がそのページから外れた瞬間にモーダルが黙って閉じてしまう。
  // 一覧の要約は version を持たないため、編集は詳細を取り直してから始める。
  const [editingIdentity, setEditingIdentity] = useState<ServiceIdentityResponse | null>(null);

  const openEdit = async (identity: ServiceIdentitySummaryResponse) => {
    try {
      setEditingIdentity(await serviceIdentityApi.get(identity.id ?? 0));
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'サービスIDの取得に失敗しました'));
    }
  };

  // 停止は実行中の定期処理を次回から止める操作なので確認を挟む
  const suspension = useDeleteAction<ServiceIdentitySummaryResponse>({
    remove: identity => serviceIdentityApi.suspend(identity.id ?? 0),
    successMessage: 'サービスIDを停止しました',
    errorMessage: 'サービスIDの停止に失敗しました',
    onDeleted: list.reload,
  });

  // 再開は元に戻す操作なので確認を挟まない
  const resume = async (identity: ServiceIdentitySummaryResponse) => {
    try {
      await serviceIdentityApi.resume(identity.id ?? 0);
      notify.success('サービスIDを再開しました');
      void list.reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'サービスIDの再開に失敗しました'));
    }
  };

  /**
   * 更新後の後始末。一覧を取り直しつつ、編集対象は id で取り直す。
   * 競合（409）でモーダルが開いたままのとき、最新の版を渡せて初めて再試行が通る。
   */
  const handleEditUpdated = () => {
    void list.reload();
    const target = editingIdentity;
    if (!target) return;
    void serviceIdentityApi
      .get(target.id ?? 0)
      // 成功保存の直後は onClose と競合するため、まだ同じ対象を開いているときだけ差し替える
      .then(fresh => setEditingIdentity(current => (current?.id === fresh.id ? fresh : current)))
      .catch(() => {
        // 取り直せないときは古い値のまま。利用者は閉じて一覧から確認できる
      });
  };

  return (
    <>
      <ListPage
        title="サービスID管理"
        description="定期処理・外部連携が使うサービスIDのロール・対象店舗を管理します。"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon />
            サービスIDを追加
          </Button>
        }
        search={{
          onSearch: () => void list.search(searchTerm),
          content: (
            <>
              <div className="w-full md:max-w-xs">
                <label htmlFor="search" className="sr-only">
                  サービスIDを検索
                </label>
                <Input
                  type="text"
                  name="search"
                  id="search"
                  value={searchTerm}
                  onChange={e => setSearchTerm(e.target.value)}
                  placeholder="用途名で検索..."
                />
              </div>
              <Button type="submit">検索</Button>
              {searchTerm && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSearchTerm('');
                    void list.search('');
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
          searchTerm ? '該当するサービスIDが見つかりません' : 'サービスIDが登録されていません'
        }
        errorMessage="サービスID一覧の取得に失敗しました"
        onRetry={list.reload}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>用途名</TableHead>
              <TableHead>ロール</TableHead>
              <TableHead>対象店舗</TableHead>
              <TableHead>状態</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {identities.map(identity => (
              <TableRow key={identity.id}>
                <TableCell className="font-medium text-foreground">
                  {identity.display_name}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {roleSetLabel(identity.roles)}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {storeSetLabel(identity.store_scope_type, identity.store_ids, stores)}
                </TableCell>
                <TableCell>
                  {identity.enabled ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      有効
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-warning/10 text-warning-strong"
                    >
                      停止中
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-primary-strong"
                    onClick={() => void openEdit(identity)}
                  >
                    編集
                  </Button>
                  {identity.enabled ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive-strong"
                      onClick={() => suspension.ask(identity)}
                    >
                      停止
                    </Button>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-primary-strong"
                      onClick={() => void resume(identity)}
                    >
                      再開
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      <ConfirmDialog
        open={suspension.target !== null}
        title="サービスIDを停止しますか？"
        description={`${suspension.target?.display_name ?? ''} を使う定期処理・外部連携は動かなくなります。サービスIDは削除されず、いつでも再開できます。`}
        confirmLabel="停止する"
        onConfirm={() => void suspension.confirm()}
        onClose={suspension.cancel}
      />

      {/* モーダルは一覧の loading / empty に連動して消えないよう外殻の外に置く。
          開くまで mount しないことで、ロール目録の取得を必要になった時点まで遅延させる */}
      {createOpen && (
        <ServiceIdentityCreateModal
          stores={stores}
          storesLoading={storesLoading}
          storesFailed={storesFailed}
          onReloadStores={() => void refetchStores()}
          onClose={() => setCreateOpen(false)}
          onCreated={list.reload}
        />
      )}
      {editingIdentity !== null && (
        <ServiceIdentityEditModal
          identity={editingIdentity}
          stores={stores}
          storesLoading={storesLoading}
          storesFailed={storesFailed}
          onReloadStores={() => void refetchStores()}
          onClose={() => setEditingIdentity(null)}
          onUpdated={handleEditUpdated}
        />
      )}
    </>
  );
}
