'use client';

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';
import { useParams } from 'next/navigation';
import { StoreStaffResponse, storeStaffApi, useStoreContext } from '@/entities/user';
import {
  StoreStaffCreateModal,
  StoreStaffEditModal,
  roleSetLabel,
} from '@/features/staff-management';
import { useKeyedResource, useListPage } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
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

/** 一覧の絞り込み条件。店舗は URL の店舗文脈が決めるので、ここでは検索語だけを持つ。 */
interface StoreStaffCriteria {
  search: string;
}

/**
 * 店舗スタッフ管理ページ。行使者は店長で、HQ はこの面へ入らない。
 *
 * 一覧は現在の店舗を担当範囲に含む「店舗側ロールのみ」のアカウントで、HQ 側ロール保持者はサーバが
 * 在否ごと外している。行ごとの編集可否は応答の editable をそのまま使う。
 */
export default function StoreStaffPage() {
  const [searchTerm, setSearchTerm] = useState('');

  const list = useListPage<StoreStaffResponse, StoreStaffCriteria>(
    (page, criteria) =>
      storeStaffApi.list({ page, size: PAGE_SIZE, search: criteria.search || undefined }),
    { search: '' }
  );
  const staff = list.rows;

  // 担当店舗の選択肢は行使者自身の授権店舗（店舗コンテキストが 1 回だけ取得済み）。
  // 付与できる範囲と同じ集合なので、選べたものはサーバの店舗部分集合検査も通る。
  const { stores, loadFailed: storesFailed, reload: reloadStores } = useStoreContext();

  const [modal, setModal] = useState<
    { kind: 'none' } | { kind: 'create' } | { kind: 'edit'; id: number }
  >({ kind: 'none' });
  const editingId = modal.kind === 'edit' ? modal.id : null;
  const storeId = useParams()?.storeId;
  const editing = useKeyedResource<StoreStaffResponse>(
    ['storeStaffApi', storeId as string, editingId],
    editingId === null ? null : () => storeStaffApi.get(editingId)
  );
  const closeModal = () => {
    if (editing.failure === 'notFound') void list.reload();
    setModal({ kind: 'none' });
  };

  const storePickerProps = {
    stores: stores ?? [],
    storesLoading: stores === null && !storesFailed,
    storesFailed,
    onReloadStores: reloadStores,
  };

  return (
    <>
      <ListPage
        title="スタッフ管理"
        description="この店舗のスタッフのロール・担当店舗を管理します。店長など、スタッフ管理の権限を持つアカウントは表示のみです。"
        actions={
          <Button onClick={() => setModal({ kind: 'create' })}>
            <PlusIcon />
            スタッフを追加
          </Button>
        }
        search={{
          onSearch: () => void list.search({ search: searchTerm }),
          content: (
            <>
              <div className="w-full md:max-w-xs">
                <label htmlFor="search" className="sr-only">
                  スタッフを検索
                </label>
                <Input
                  type="text"
                  name="search"
                  id="search"
                  value={searchTerm}
                  onChange={e => setSearchTerm(e.target.value)}
                  placeholder="氏名またはメールアドレスで検索..."
                />
              </div>
              <Button type="submit">検索</Button>
              {searchTerm && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSearchTerm('');
                    void list.search({ search: '' });
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
          searchTerm ? '該当するスタッフが見つかりません' : 'スタッフが登録されていません'
        }
        errorMessage="スタッフ一覧の取得に失敗しました"
        onRetry={list.reload}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>氏名</TableHead>
              <TableHead>メールアドレス</TableHead>
              <TableHead>ロール</TableHead>
              <TableHead>状態</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {staff.map(member => (
              <TableRow key={member.id}>
                <TableCell className="font-medium text-foreground">{member.display_name}</TableCell>
                <TableCell className="text-muted-foreground">{member.email}</TableCell>
                <TableCell className="text-muted-foreground">
                  {roleSetLabel(member.roles)}
                </TableCell>
                <TableCell>
                  {member.enabled ? (
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
                  {member.editable ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-primary-strong"
                      onClick={() => setModal({ kind: 'edit', id: member.id ?? 0 })}
                    >
                      編集
                    </Button>
                  ) : (
                    // 押せない導線を出すより、権限が及ばないことをその場で名乗る。
                    <span className="pr-3 text-xs text-muted-foreground">編集権限なし</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      {/* モーダルは一覧の loading / empty に連動して消えないよう外殻の外に置く。
          開くまで mount しないことで、可授ロールの取得を必要になった時点まで遅延させる */}
      {modal.kind === 'create' && (
        <StoreStaffCreateModal
          {...storePickerProps}
          onClose={() => closeModal()}
          onCreated={list.reload}
        />
      )}
      {modal.kind === 'edit' && (
        <StoreStaffEditModal
          resource={editing}
          {...storePickerProps}
          onClose={closeModal}
          onUpdated={list.reload}
        />
      )}
    </>
  );
}
