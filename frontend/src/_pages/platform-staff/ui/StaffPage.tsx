'use client';

import { PlusIcon } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
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
  // 適用済みの検索語。入力の state をそのまま読むと、値を変えた同一ハンドラ内で再取得したとき
  // 再レンダー前の古い値で取得してしまう（useListPage の search 制約）ため ref で持つ。
  const appliedSearch = useRef('');

  const list = useListPage(
    page =>
      platformStaffApi.list({
        page,
        size: PAGE_SIZE,
        search: appliedSearch.current || undefined,
      }),
    'スタッフ一覧の取得に失敗しました'
  );
  const staff = list.rows;
  const { items: stores } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );

  /** 検索語を適用して 1 ページ目から取り直す */
  const applySearch = (term: string) => {
    appliedSearch.current = term;
    list.search();
  };
  const [createOpen, setCreateOpen] = useState(false);
  // 編集対象は一覧から独立して保持する。分頁後の現在ページから導出すると、409 の再取得で
  // 対象がそのページから外れた瞬間（本人が PUT /platform/me で改名して検索から外れる、
  // 他の管理者の追加で行が次ページへずれる等）にモーダルが黙って閉じ、
  // 「最新の内容を確認してください」と言いながら内容を見せない状態になる。
  const [editingStaff, setEditingStaff] = useState<PlatformStaffResponse | null>(null);
  // 再取得した頁に対象が居れば最新値へ差し替える（これが 409 リフレッシュの本体）。
  // 居なければ直前の値を保つ — 版が古いままなので再試行は再び 409 になるが、
  // 競合を伝えたうえで利用者が閉じるか取り直すかを選べる。
  useEffect(() => {
    setEditingStaff(current =>
      current ? (staff.find(member => member.id === current.id) ?? current) : current
    );
  }, [staff]);

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
          onSearch: () => applySearch(searchTerm),
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
              <TableRow
                key={member.id}
                className="cursor-pointer"
                onClick={() => setEditingStaff(member)}
              >
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
                    onClick={e => {
                      e.stopPropagation();
                      setEditingStaff(member);
                    }}
                  >
                    編集
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      {/* モーダルは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <StaffCreateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={list.reload}
      />
      <StaffEditModal
        open={editingStaff !== null}
        staff={editingStaff}
        onClose={() => setEditingStaff(null)}
        onUpdated={list.reload}
      />
    </>
  );
}
