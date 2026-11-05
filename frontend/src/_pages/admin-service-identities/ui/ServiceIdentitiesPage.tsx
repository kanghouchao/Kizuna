'use client';

import { PlusIcon } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import {
  ServiceIdentitySummaryResponse,
  serviceIdentityApi,
  useStoreContext,
} from '@/entities/user';
import { roleSetLabel, storeSetLabel } from '@/features/staff-management';
import { getApiErrorMessage, useDeleteAction, useListPage } from '@/shared/lib';
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
 * サービスID管理ページ。対話ログインできない実行主体（バッチ・外部連携）の一覧と停止・再開を行い、
 * 作成・授権編集は別画面へ送る。人のアカウント管理とは別の面。
 */
export default function ServiceIdentitiesPage() {
  const [searchTerm, setSearchTerm] = useState('');

  const list = useListPage<ServiceIdentitySummaryResponse, string>(
    (page, search) =>
      serviceIdentityApi.list({ page, size: PAGE_SIZE, search: search || undefined }),
    ''
  );
  const identities = list.rows;
  // 対象店舗の表示に使う店舗目録（行使者の授権店舗）
  const stores = useStoreContext().stores ?? [];

  // 停止は実行中の定期処理を次回から止める操作なので確認を挟む
  const suspension = useDeleteAction<ServiceIdentitySummaryResponse>({
    remove: identity => serviceIdentityApi.suspend(identity.id ?? 0),
    successMessage: 'サービスIDを停止しました',
    errorMessage: 'サービスIDの停止に失敗しました',
    reload: list.reload,
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
          <Button render={<Link href="/admin/service-identities/create" />}>
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
                    render={<Link href={`/admin/service-identities/${identity.id}/edit`} />}
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
        open={suspension.open}
        title="サービスIDを停止しますか？"
        description={`${suspension.target?.display_name ?? ''} を使う定期処理・外部連携は動かなくなります。サービスIDは削除されず、いつでも再開できます。`}
        confirmLabel="停止する"
        onConfirm={() => void suspension.confirm()}
        onClose={suspension.cancel}
      />
    </>
  );
}
