'use client';

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';
import {
  PlatformStaffResponse,
  PlatformStore,
  platformAuthApi,
  platformStaffApi,
} from '@/entities/user';
import {
  StaffCreateModal,
  StaffEditModal,
  roleSetLabel,
  storeSetLabel,
} from '@/features/staff-management';
import { useListPage, useManagedList } from '@/shared/lib';
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

/** スタッフ一覧ページ。一覧内モーダルで新規作成・編集を行う。 */
export default function StaffPage() {
  const [searchTerm, setSearchTerm] = useState('');

  const list = useListPage<PlatformStaffResponse, string>(
    (page, search) =>
      platformStaffApi.list({
        page,
        size: PAGE_SIZE,
        search: search || undefined,
      }),
    'スタッフ一覧の取得に失敗しました',
    ''
  );
  const staff = list.rows;
  // 店舗目録はページで 1 回だけ取得し、一覧の担当店舗ラベルとモーダル（担当店舗の選択・要約）で共有する
  const { items: stores, isLoading: storesLoading } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );

  const [createOpen, setCreateOpen] = useState(false);
  // 編集対象は一覧から独立して保持する。分頁後の現在ページから導出すると、409 の再取得で
  // 対象がそのページから外れた瞬間（本人が PUT /platform/me で改名して検索から外れる、
  // 他の管理者の追加で行が次ページへずれる等）にモーダルが黙って閉じ、
  // 「最新の内容を確認してください」と言いながら内容を見せない状態になる。
  const [editingStaff, setEditingStaff] = useState<PlatformStaffResponse | null>(null);

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
    void platformStaffApi
      .get(target.id ?? 0)
      // 成功保存の直後は onClose と競合するため、まだ同じ対象を開いているときだけ差し替える
      .then(fresh => setEditingStaff(current => (current?.id === fresh.id ? fresh : current)))
      .catch(() => {
        // 取り直せない（削除済み等）ときは古い値のまま。利用者は閉じて一覧から確認できる
      });
  };

  return (
    <>
      <ListPage
        title="スタッフ管理"
        description="ロール・担当店舗の付与と編集ができます。"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon />
            スタッフを追加
          </Button>
        }
        search={{
          onSearch: () => void list.search(searchTerm),
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
          searchTerm ? '該当するスタッフが見つかりません' : 'スタッフが登録されていません'
        }
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>氏名</TableHead>
              <TableHead>ロール</TableHead>
              <TableHead>状態</TableHead>
              <TableHead>担当店舗</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {staff.map(member => (
              <TableRow key={member.id}>
                <TableCell className="font-medium text-foreground">{member.display_name}</TableCell>
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
                <TableCell className="text-muted-foreground">
                  {storeSetLabel(member.store_scope_type, member.store_ids, stores)}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-primary-strong"
                    onClick={() => setEditingStaff(member)}
                  >
                    編集
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      {/* モーダルは一覧の loading / empty に連動して消えないよう外殻の外に置く。
          開くまで mount しないことで、ロール目録の取得を必要になった時点まで遅延させる */}
      {createOpen && (
        <StaffCreateModal
          stores={stores}
          storesLoading={storesLoading}
          onClose={() => setCreateOpen(false)}
          onCreated={list.reload}
        />
      )}
      {editingStaff !== null && (
        <StaffEditModal
          staff={editingStaff}
          stores={stores}
          storesLoading={storesLoading}
          onClose={() => setEditingStaff(null)}
          onUpdated={handleEditUpdated}
        />
      )}
    </>
  );
}
