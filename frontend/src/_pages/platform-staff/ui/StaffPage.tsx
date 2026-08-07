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
  RegionError,
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

/** 店舗で絞り込まないことを表す番兵値（命名は既存の SELECT_NONE に倣う）。 */
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
    { search: '' }
  );
  const staff = list.rows;
  // 店舗目録はページで 1 回だけ取得し、検索欄の絞り込みとモーダル（担当店舗の選択・要約）で共有する
  const {
    items: stores,
    isLoading: storesLoading,
    failed: storesFailed,
    refetch: refetchStores,
  } = useManagedList<PlatformStore>(() => platformAuthApi.stores());

  const [createOpen, setCreateOpen] = useState(false);
  // 編集対象は一覧から独立して保持する。分頁後の現在ページから導出すると、409 の再取得で
  // 対象がそのページから外れた瞬間（本人が PUT /platform/me で改名して検索から外れる、
  // 他の管理者の追加で行が次ページへずれる等）にモーダルが黙って閉じ、
  // 「最新の内容を確認してください」と言いながら内容を見せない状態になる。
  const [editingStaff, setEditingStaff] = useState<PlatformStaffResponse | null>(null);

  // モーダルを開くたびに目録を取り直す（他管理者の店舗追加・削除への追随。現有目録は
  // 表示したまま、届き次第差し替わる）。開いた瞬間がまだ読み込み中で、その後に失敗が
  // 確定する時序では、settle 後の失敗を検知して 1 回だけ取り直す（失敗が続く環境で無限に
  // 叩かない — それ以降の回復は StoreSetPicker の再試行導線が担う）。
  const modalOpen = createOpen || editingStaff !== null;
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

  // 取り直した目録から選択中の店舗が消えたら（他管理者の削除）、トリガー表示が空白のまま
  // 絞り込みだけが効き続ける見えない状態になるため、「すべての店舗」へ戻して取り直す。
  // 取得失敗は目録を空にするため、失敗を除かないと 1 回の通信エラーが利用者の絞り込みを
  // 黙って解除してしまう（「消えた」と「読めなかった」は別物で、後者は目録の事実ではない）。
  // 背景処理のため入力欄の下書き（searchTerm）は読まず、適用済み条件から店舗だけを外す。
  useEffect(() => {
    if (storeFilter === ALL_STORES || storesLoading || storesFailed) return;
    if (stores.some(store => String(store.id) === storeFilter)) return;
    setStoreFilter(ALL_STORES);
    void list.search(prev => ({ ...prev, storeId: undefined }));
  }, [stores, storesLoading, storesFailed, storeFilter, list]);

  // 引き金に出る文言は候補一覧から引かれるので、選べる値はここ一箇所に持つ。
  const storeFilterOptions = [
    { value: ALL_STORES, label: 'すべての店舗' },
    ...stores
      .filter(store => store.id !== undefined)
      .map(store => ({ value: String(store.id), label: store.name ?? String(store.id) })),
  ];

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
                items={storeFilterOptions}
                value={storeFilter}
                onValueChange={(selected, details) => {
                  // 候補から消えた値の巻き戻し（先頭項目へ倒される）でもここは鳴る。拾うのは
                  // 利用者が項目を押したときだけ——巻き戻しは上の effect が適用済み条件を
                  // 保ったまま担うので、下書きの検索語で上書きしてはいけない。
                  if (details.reason !== 'item-press' || selected === null) return;
                  setStoreFilter(selected);
                  void list.search({
                    search: searchTerm,
                    storeId: selected === ALL_STORES ? undefined : Number(selected),
                  });
                }}
              >
                <SelectTrigger aria-label="店舗で絞り込む" className="w-full md:w-56">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {storeFilterOptions.map(o => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {/* 目録が読めないと引き金は店舗名を引けず id を出す。黙って劣化させず、
                  選択肢の領域として自分の失敗を名乗る（絞り込み自体は解除しない） */}
              {storesFailed && (
                <RegionError
                  message="店舗一覧の取得に失敗しました"
                  onRetry={() => void refetchStores()}
                />
              )}
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
          storesFailed={storesFailed}
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
          storesFailed={storesFailed}
          onReloadStores={() => void refetchStores()}
          onClose={() => setEditingStaff(null)}
          onUpdated={handleEditUpdated}
        />
      )}
    </>
  );
}
