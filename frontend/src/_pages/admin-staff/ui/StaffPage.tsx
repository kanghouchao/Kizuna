'use client';

import { PlusIcon } from 'lucide-react';
import { useRef, useState } from 'react';
import { StaffResponse, staffApi, useStoreContext, hasPermission, useMe } from '@/entities/user';
import { StaffCreateModal, StaffEditModal, roleSetLabel } from '@/features/staff-management';
import { getApiErrorMessage, useDeleteAction, useKeyedResource, useListPage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
  ConfirmDialog,
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
import { TemporaryPasswordModal } from './TemporaryPasswordModal';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 10;

/** 店舗で絞り込まないことを表す番兵値（命名は既存の SELECT_NONE に倣う）。 */
const ALL_STORES = '__all__';

/** 一覧の絞り込み条件。検索語と店舗のどちらを変えても 1 ページ目から取り直す。 */
interface StaffCriteria {
  search: string;
  storeId?: number;
}

/**
 * 従業員管理ページ。行使者の店舗集合と交わる従業員を並べ、授権の編集とアカウント状態の操作を行う。
 * 行ごとの操作可否は応答の editable（サーバの委譲守衛の判定）をそのまま使う。
 */
export default function StaffPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [storeFilter, setStoreFilter] = useState(ALL_STORES);
  const storeId = storeFilter === ALL_STORES ? undefined : Number(storeFilter);

  const list = useListPage<StaffResponse, StaffCriteria>(
    (page, criteria) =>
      staffApi.list({
        page,
        size: PAGE_SIZE,
        search: criteria.search || undefined,
        storeId: criteria.storeId,
      }),
    { search: '' }
  );
  const staff = list.rows;

  // 担当店舗の選択肢は行使者自身の授権店舗。付与できる範囲と同じ集合なので、
  // 選べたものはサーバの店舗部分集合検査も通る。
  const { stores, loadFailed: storesFailed, reload: reloadStores } = useStoreContext();
  const storePickerProps = {
    stores: stores ?? [],
    storesLoading: stores === null && !storesFailed,
    storesFailed,
    onReloadStores: reloadStores,
  };

  // 停止・再開・パスワード再設定は一覧とは別の権限。強制はサーバ側で、ここは導線を出すかだけを決める。
  const canManageAccounts = hasPermission(useMe().me, 'STAFF_CREDENTIAL_MANAGE');

  const [modal, setModal] = useState<
    { kind: 'none' } | { kind: 'create' } | { kind: 'edit'; id: number }
  >({ kind: 'none' });
  const editingId = modal.kind === 'edit' ? modal.id : null;
  const editing = useKeyedResource<StaffResponse>(
    ['staffApi', editingId],
    editingId === null ? null : () => staffApi.get(editingId)
  );
  const closeModal = () => {
    if (editing.failure === 'notFound') void list.reload();
    setModal({ kind: 'none' });
  };

  // 停止は対象のセッションを即時に失効させるため確認を挟む。拒否の理由（自分自身・
  // 最後の管理権限保持者・委譲外）はサーバだけが持つので、文言は応答からそのまま出す。
  const suspension = useDeleteAction<StaffResponse>({
    remove: member => staffApi.suspend(member.id ?? 0),
    successMessage: 'アカウントを停止しました',
    errorMessage: 'アカウントの停止に失敗しました',
    onDeleted: list.reload,
  });

  // 再設定の確認対象と発行された仮パスワードはページに置く — 行に持たせると
  // 成功後の一覧取り直しで行ごと unmount され、表示が消える。
  const [resetTargetOpen, setResetTargetOpen] = useState(false);
  const [resetTarget, setResetTarget] = useState<StaffResponse | null>(null);
  const [issuedOpen, setIssuedOpen] = useState(false);
  const [issued, setIssued] = useState<{
    displayName: string;
    email: string;
    password: string;
  } | null>(null);

  // 応答は一度きり表示の仮パスワードを運び、表示枠は 1 つしかない。重なると有効な値が
  // 失われるため、進行中は全行の入口を塞いで直列化し、最新要求以外の応答も世代守衛で棄てる。
  const [resetting, setResetting] = useState(false);
  const resetRequestIdRef = useRef(0);

  const resetPassword = async (member: StaffResponse) => {
    setResetTargetOpen(false);
    setResetting(true);
    const requestId = ++resetRequestIdRef.current;
    try {
      const result = await staffApi.resetPassword(member.id ?? 0);
      if (requestId !== resetRequestIdRef.current) return;
      setIssued({
        displayName: member.display_name ?? '',
        email: member.email ?? '',
        password: result.temporary_password,
      });
      setIssuedOpen(true);
    } catch (error) {
      if (requestId !== resetRequestIdRef.current) return;
      notify.error(getApiErrorMessage(error, 'パスワードの再設定に失敗しました'));
    } finally {
      setResetting(false);
    }
  };

  // 再開は元に戻す操作なので確認を挟まない
  const resume = async (member: StaffResponse) => {
    try {
      await staffApi.resume(member.id ?? 0);
      notify.success('アカウントを再開しました');
      void list.reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'アカウントの再開に失敗しました'));
    }
  };

  // 引き金に出る文言は候補一覧から引かれるので、選べる値はここ一箇所に持つ。
  const storeFilterOptions = [
    { value: ALL_STORES, label: 'すべての店舗' },
    ...(stores ?? [])
      .filter(store => store.id !== undefined)
      .map(store => ({ value: String(store.id), label: store.name ?? String(store.id) })),
  ];

  return (
    <>
      <ListPage
        title="従業員管理"
        description="従業員のロール・担当店舗・アカウント状態を管理します。"
        actions={
          <Button onClick={() => setModal({ kind: 'create' })}>
            <PlusIcon />
            従業員を追加
          </Button>
        }
        search={{
          onSearch: () => void list.search({ search: searchTerm, storeId }),
          content: (
            <>
              <div className="w-full md:max-w-xs">
                <label htmlFor="search" className="sr-only">
                  従業員を検索
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
                  // 候補から消えた値の巻き戻しでもここは鳴る。拾うのは利用者が項目を押したときだけ。
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
              {/* 目録が読めないと引き金は店舗名を引けない。選択肢の領域として自分の失敗を名乗る */}
              {storesFailed && (
                <RegionError message="店舗一覧の取得に失敗しました" onRetry={reloadStores} />
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
          // 店舗の絞り込み中も「登録なし」ではなく「該当なし」（他店舗には従業員が居る可能性がある）
          searchTerm || storeId !== undefined
            ? '該当する従業員が見つかりません'
            : '従業員が登録されていません'
        }
        errorMessage="従業員一覧の取得に失敗しました"
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
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-primary-strong"
                        onClick={() => setModal({ kind: 'edit', id: member.id ?? 0 })}
                      >
                        編集
                      </Button>
                      {canManageAccounts && (
                        <>
                          {member.enabled ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-destructive-strong"
                              onClick={() => suspension.ask(member)}
                            >
                              停止
                            </Button>
                          ) : (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-primary-strong"
                              onClick={() => void resume(member)}
                            >
                              再開
                            </Button>
                          )}
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={resetting}
                            onClick={() => {
                              setResetTarget(member);
                              setResetTargetOpen(true);
                            }}
                          >
                            パスワード再設定
                          </Button>
                        </>
                      )}
                    </div>
                  ) : (
                    // 押せない導線を出すより、権限が及ばないことをその場で名乗る。
                    <span className="pr-3 text-xs text-muted-foreground">操作権限なし</span>
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
        <StaffCreateModal
          {...storePickerProps}
          onClose={() => closeModal()}
          onCreated={list.reload}
        />
      )}
      {modal.kind === 'edit' && (
        <StaffEditModal
          resource={editing}
          {...storePickerProps}
          onClose={closeModal}
          onUpdated={list.reload}
        />
      )}

      <ConfirmDialog
        open={suspension.open}
        title="アカウントを停止しますか？"
        description={`${suspension.target?.display_name ?? ''} のセッションは即時に失効し、ログインできなくなります。アカウントは削除されず、いつでも再開できます。`}
        confirmLabel="停止する"
        onConfirm={() => void suspension.confirm()}
        onClose={suspension.cancel}
      />

      <ConfirmDialog
        open={resetTargetOpen}
        title="パスワードを再設定しますか？"
        description={`${resetTarget?.display_name ?? ''} のセッションは即時に失効します。仮パスワードは発行直後に一度だけ表示され、閉じると二度と確認できません。`}
        confirmLabel="再設定する"
        onConfirm={() => void (resetTarget && resetPassword(resetTarget))}
        onClose={() => setResetTargetOpen(false)}
      />

      <TemporaryPasswordModal
        open={issuedOpen}
        temporaryPassword={issued?.password ?? ''}
        displayName={issued?.displayName ?? ''}
        email={issued?.email ?? ''}
        onClose={() => setIssuedOpen(false)}
        onClosed={() => setIssued(null)}
      />
    </>
  );
}
