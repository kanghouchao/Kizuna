'use client';

import { PlusIcon } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
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
import {
  getApiErrorMessage,
  useKeyedResource,
  useDeleteAction,
  useListPage,
  useManagedList,
} from '@/shared/lib';
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

  const [modal, setModal] = useState<
    { kind: 'none' } | { kind: 'create' } | { kind: 'edit'; id: number }
  >({ kind: 'none' });
  const editingId = modal.kind === 'edit' ? modal.id : null;
  const editing = useKeyedResource<ServiceIdentityResponse>(
    ['serviceIdentity', editingId],
    editingId === null ? null : () => serviceIdentityApi.get(editingId)
  );
  const openCreate = () => setModal({ kind: 'create' });
  const openEdit = (identity: ServiceIdentitySummaryResponse) =>
    setModal({ kind: 'edit', id: identity.id ?? 0 });
  const closeModal = () => {
    if (editing.failure === 'notFound') void list.reload();
    setModal({ kind: 'none' });
  };

  // モーダルを開くたびに店舗目録を取り直す（他管理者の店舗追加・削除への追随。現有目録は
  // 表示したまま、届き次第差し替わる）。開いた瞬間がまだ読み込み中で、その後に失敗が
  // 確定する時序では、settle 後の失敗を検知して 1 回だけ取り直す（失敗が続く環境で無限に
  // 叩かない — それ以降の回復は StoreSetPicker の再試行導線が担う）。
  const modalOpen = modal.kind !== 'none';
  const prevModalOpenRef = useRef(false);
  const storesRetriedRef = useRef(false);
  useEffect(() => {
    const justOpened = modalOpen && !prevModalOpenRef.current;
    prevModalOpenRef.current = modalOpen;
    if (justOpened) {
      if (storesLoading) {
        // まだ読み込み中: settle 後の失敗にそなえて自動再試行の権利を残す
        storesRetriedRef.current = false;
      } else {
        // 開幕の取り直し自体を 1 回目と数え、直後に失敗で settle しても連打しない
        storesRetriedRef.current = true;
        void refetchStores();
      }
      return;
    }
    if (modalOpen && !storesLoading && storesFailed && !storesRetriedRef.current) {
      storesRetriedRef.current = true;
      void refetchStores();
    }
  }, [modalOpen, storesLoading, storesFailed, refetchStores]);

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

  return (
    <>
      <ListPage
        title="サービスID管理"
        description="定期処理・外部連携が使うサービスIDのロール・対象店舗を管理します。"
        actions={
          <Button onClick={openCreate}>
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
      {modal.kind === 'create' && (
        <ServiceIdentityCreateModal
          stores={stores}
          storesLoading={storesLoading}
          storesFailed={storesFailed}
          onReloadStores={() => void refetchStores()}
          onClose={closeModal}
          onCreated={list.reload}
        />
      )}
      {modal.kind === 'edit' && (
        <ServiceIdentityEditModal
          resource={editing}
          stores={stores}
          storesLoading={storesLoading}
          storesFailed={storesFailed}
          onReloadStores={() => void refetchStores()}
          onClose={closeModal}
          onUpdated={list.reload}
        />
      )}
    </>
  );
}
