'use client';

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';
import { StoreStaffResponse, storeStaffApi, useStoreContext } from '@/entities/user';
import {
  StoreStaffCreateModal,
  StoreStaffEditModal,
  roleSetLabel,
} from '@/features/staff-management';
import { useListPage } from '@/shared/lib';
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

  const [createOpen, setCreateOpen] = useState(false);
  // 編集対象は一覧から独立して保持する。分頁後の現在ページから導出すると、409 の再取得で
  // 対象がそのページから外れた瞬間にモーダルが黙って閉じ、「最新の内容を確認してください」と
  // 言いながら内容を見せない状態になる。
  const [editingStaff, setEditingStaff] = useState<StoreStaffResponse | null>(null);

  const storePickerProps = {
    stores: stores ?? [],
    storesLoading: stores === null && !storesFailed,
    storesFailed,
    onReloadStores: reloadStores,
  };

  /**
   * 更新後の後始末。一覧を取り直しつつ、編集対象は id で取り直す。
   *
   * 競合（409）でモーダルが開いたままのとき、最新の版を渡せて初めて再試行が通る。
   * 一覧の現在ページから導出すると対象が頁の外にいる場合に古い版のままとなり、
   * 再試行が 409 を繰り返すため、頁とは無関係な id 取得で最新化する。
   */
  const handleEditUpdated = () => {
    void list.reload();
    const target = editingStaff;
    if (!target) return;
    void storeStaffApi
      .get(target.id ?? 0)
      // 成功保存の直後は onClose と競合するため、まだ同じ対象を開いているときだけ差し替える
      .then(fresh => setEditingStaff(current => (current?.id === fresh.id ? fresh : current)))
      .catch(() => {
        // 取り直せない（停止・付け替えで対象外になった等）ときは古い値のまま。利用者は閉じて一覧から確認できる
      });
  };

  return (
    <>
      <ListPage
        title="スタッフ管理"
        description="この店舗のスタッフのロール・担当店舗を管理します。店長など、スタッフ管理の権限を持つアカウントは表示のみです。"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
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
                      onClick={() => setEditingStaff(member)}
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
      {createOpen && (
        <StoreStaffCreateModal
          {...storePickerProps}
          onClose={() => setCreateOpen(false)}
          onCreated={list.reload}
        />
      )}
      {editingStaff !== null && (
        <StoreStaffEditModal
          staff={editingStaff}
          {...storePickerProps}
          onClose={() => setEditingStaff(null)}
          onUpdated={handleEditUpdated}
        />
      )}
    </>
  );
}
