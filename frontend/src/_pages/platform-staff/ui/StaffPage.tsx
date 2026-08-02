'use client';

import { PlusIcon } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import {
  PlatformStaffResponse,
  PlatformStore,
  platformAuthApi,
  platformStaffApi,
} from '@/entities/user';
import { StaffCreateModal, StaffEditModal, roleSetLabel } from '@/features/staff-management';
import { useListPage, useManagedList } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 10;

/** 店舗で絞り込まないことを表す番兵値（Radix Select は空文字を値に採れない。命名は既存の SELECT_NONE に倣う）。 */
const ALL_STORES = '__all__';

/** 一覧の絞り込み条件。検索語と店舗のどちらを変えても 1 ページ目から取り直す。 */
interface StaffCriteria {
  search: string;
  storeId?: number;
}

/** スタッフ一覧ページ。一覧内モーダルで新規作成・編集を行う。 */
export default function StaffPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [storeFilter, setStoreFilter] = useState(ALL_STORES);
  const storeId = storeFilter === ALL_STORES ? undefined : Number(storeFilter);

  const list = useListPage<PlatformStaffResponse, StaffCriteria>(
    (page, criteria) =>
      platformStaffApi.list({
        page,
        size: PAGE_SIZE,
        search: criteria.search || undefined,
        storeId: criteria.storeId,
      }),
    'スタッフ一覧の取得に失敗しました',
    { search: '' }
  );
  const staff = list.rows;
  // 店舗目録はページで 1 回だけ取得し、検索欄の絞り込みとモーダル（担当店舗の選択・要約）で共有する
  const {
    items: stores,
    isLoading: storesLoading,
    refetch: refetchStores,
  } = useManagedList<PlatformStore>(() => platformAuthApi.stores(), '店舗一覧の取得に失敗しました');

  const [createOpen, setCreateOpen] = useState(false);
  // 編集対象は一覧から独立して保持する。分頁後の現在ページから導出すると、409 の再取得で
  // 対象がそのページから外れた瞬間（本人が PUT /platform/me で改名して検索から外れる、
  // 他の管理者の追加で行が次ページへずれる等）にモーダルが黙って閉じ、
  // 「最新の内容を確認してください」と言いながら内容を見せない状態になる。
  const [editingStaff, setEditingStaff] = useState<PlatformStaffResponse | null>(null);

  // モーダルを開くたびに目録を取り直す（他管理者の店舗追加・削除への追随。現有目録は
  // 表示したまま、届き次第差し替わる）。開いた瞬間がまだ読み込み中で、その後に失敗が
  // 確定する時序では、settle 後の空を検知して 1 回だけ取り直す（失敗が続く環境で無限に
  // 叩かない — それ以降の回復は StoreSetPicker の再読み込み導線が担う）。
  const modalOpen = createOpen || editingStaff !== null;
  const prevModalOpenRef = useRef(false);
  const storesRetriedRef = useRef(false);
  useEffect(() => {
    const justOpened = modalOpen && !prevModalOpenRef.current;
    prevModalOpenRef.current = modalOpen;
    if (justOpened) {
      if (storesLoading) {
        // まだ読み込み中: settle 後の空にそなえて自動再試行の権利を残す
        storesRetriedRef.current = false;
      } else {
        // 開幕の取り直し自体を 1 回目と数え、直後に空で settle しても連打しない
        storesRetriedRef.current = true;
        void refetchStores();
      }
      return;
    }
    if (modalOpen && !storesLoading && stores.length === 0 && !storesRetriedRef.current) {
      storesRetriedRef.current = true;
      void refetchStores();
    }
  }, [modalOpen, storesLoading, stores.length, refetchStores]);

  // 取り直した目録から選択中の店舗が消えたら（他管理者の削除）、トリガー表示が空白のまま
  // 絞り込みだけが効き続ける見えない状態になるため、「すべての店舗」へ戻して取り直す。
  // 取得失敗は既存目録を保つ（useManagedList）ので、失敗でここが誤発火することはない。
  // 背景処理のため入力欄の下書き（searchTerm）は読まず、適用済み条件から店舗だけを外す。
  useEffect(() => {
    if (storeFilter === ALL_STORES || storesLoading) return;
    if (stores.some(store => String(store.id) === storeFilter)) return;
    setStoreFilter(ALL_STORES);
    void list.search(prev => ({ ...prev, storeId: undefined }));
  }, [stores, storesLoading, storeFilter, list]);

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
          onSearch: () => void list.search({ search: searchTerm, storeId }),
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
              {/* 店舗の選択は検索ボタンを待たずに即時適用する（選び直すたびに一覧が変わる方が自然なため） */}
              <Select
                value={storeFilter}
                onValueChange={value => {
                  setStoreFilter(value);
                  void list.search({
                    search: searchTerm,
                    storeId: value === ALL_STORES ? undefined : Number(value),
                  });
                }}
              >
                <SelectTrigger aria-label="店舗で絞り込む" className="w-full md:w-56">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_STORES}>すべての店舗</SelectItem>
                  {stores.map(store =>
                    store.id === undefined ? null : (
                      <SelectItem key={store.id} value={String(store.id)}>
                        {store.name ?? String(store.id)}
                      </SelectItem>
                    )
                  )}
                </SelectContent>
              </Select>
              <Button type="submit">検索</Button>
              {searchTerm && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSearchTerm('');
                    void list.search({ search: '', storeId });
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
          // 店舗の絞り込み中も「登録なし」ではなく「該当なし」（他店舗にはスタッフが居る可能性がある）
          searchTerm || storeId !== undefined
            ? '該当するスタッフが見つかりません'
            : 'スタッフが登録されていません'
        }
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
          onReloadStores={() => void refetchStores()}
          onClose={() => setCreateOpen(false)}
          onCreated={list.reload}
        />
      )}
      {editingStaff !== null && (
        <StaffEditModal
          staff={editingStaff}
          stores={stores}
          storesLoading={storesLoading}
          onReloadStores={() => void refetchStores()}
          onClose={() => setEditingStaff(null)}
          onUpdated={handleEditUpdated}
        />
      )}
    </>
  );
}
