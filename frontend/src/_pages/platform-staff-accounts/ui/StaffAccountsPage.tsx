'use client';

import { useRef, useState } from 'react';
import { StaffAccountSummaryResponse, platformStaffAccountApi } from '@/entities/user';
import { roleSetLabel } from '@/features/staff-management';
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
import { TemporaryPasswordModal } from './TemporaryPasswordModal';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 10;

/**
 * アカウント管理ページ。全スタッフアカウントの状態を並べ、停止・再開とパスワード再設定だけを行う。
 * この面は授権を一切動かさない（ロールは表示専用で、編集への導線も持たない）。
 */
export default function StaffAccountsPage() {
  const [searchTerm, setSearchTerm] = useState('');

  const list = useListPage<StaffAccountSummaryResponse, string>(
    (page, search) =>
      platformStaffAccountApi.list({ page, size: PAGE_SIZE, search: search || undefined }),
    ''
  );
  const accounts = list.rows;

  // 停止は対象のセッションを即時に失効させるため確認を挟む。拒否の理由（自分自身・
  // 最後の管理権限保持者）はサーバだけが持つので、文言は応答からそのまま出す。
  const suspension = useDeleteAction<StaffAccountSummaryResponse>({
    remove: account => platformStaffAccountApi.suspend(account.id ?? 0),
    successMessage: 'アカウントを停止しました',
    errorMessage: 'アカウントの停止に失敗しました',
    onDeleted: list.reload,
  });

  // 再設定の確認対象と、発行された仮パスワードの表示。どちらもページに置く —
  // 行に持たせると成功後の一覧取り直しで行ごと unmount され、表示が消える。
  const [resetTarget, setResetTarget] = useState<StaffAccountSummaryResponse | null>(null);
  const [issued, setIssued] = useState<{ displayName: string; password: string } | null>(null);

  // 応答順は要求順と一致しない。応答は一度きり表示の仮パスワードを運ぶため、遅れて届いた
  // 古い応答が最新の（唯一有効な）値を上書きしないよう世代で守り、最新要求以外は棄てる。
  const resetRequestIdRef = useRef(0);

  // 一覧の型では HQ 側ロールを判別できないので行は出し分けない。対象外（HQ 側ロール保持者・
  // 自分自身）の拒否はサーバだけが判定でき、文言は応答からそのまま出す。
  const resetPassword = async (account: StaffAccountSummaryResponse) => {
    setResetTarget(null);
    const requestId = ++resetRequestIdRef.current;
    try {
      const result = await platformStaffAccountApi.resetPassword(account.id ?? 0);
      if (requestId !== resetRequestIdRef.current) return;
      setIssued({
        displayName: account.display_name ?? '',
        password: result.temporary_password,
      });
    } catch (error) {
      if (requestId !== resetRequestIdRef.current) return;
      notify.error(getApiErrorMessage(error, 'パスワードの再設定に失敗しました'));
    }
  };

  // 再開は元に戻す操作なので確認を挟まない
  const resume = async (account: StaffAccountSummaryResponse) => {
    try {
      await platformStaffAccountApi.resume(account.id ?? 0);
      notify.success('アカウントを再開しました');
      void list.reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'アカウントの再開に失敗しました'));
    }
  };

  return (
    <>
      <ListPage
        title="アカウント管理"
        description="スタッフアカウントの状態を確認し、停止・再開します。"
        search={{
          onSearch: () => void list.search(searchTerm),
          content: (
            <>
              <div className="w-full md:max-w-xs">
                <label htmlFor="search" className="sr-only">
                  アカウントを検索
                </label>
                <Input
                  type="text"
                  name="search"
                  id="search"
                  value={searchTerm}
                  onChange={e => setSearchTerm(e.target.value)}
                  placeholder="表示名またはメールアドレスで検索..."
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
          searchTerm ? '該当するアカウントが見つかりません' : 'アカウントが登録されていません'
        }
        errorMessage="アカウント一覧の取得に失敗しました"
        onRetry={list.reload}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>表示名</TableHead>
              <TableHead>メールアドレス</TableHead>
              <TableHead>ロール</TableHead>
              <TableHead>状態</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {accounts.map(account => (
              <TableRow key={account.id}>
                <TableCell className="font-medium text-foreground">
                  {account.display_name}
                </TableCell>
                <TableCell className="text-muted-foreground">{account.email}</TableCell>
                <TableCell className="text-muted-foreground">
                  {roleSetLabel(account.roles)}
                </TableCell>
                <TableCell>
                  {account.enabled ? (
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
                  <Button variant="ghost" size="sm" onClick={() => setResetTarget(account)}>
                    パスワード再設定
                  </Button>
                  {account.enabled ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive-strong"
                      onClick={() => suspension.ask(account)}
                    >
                      停止
                    </Button>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-primary-strong"
                      onClick={() => void resume(account)}
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
        title="アカウントを停止しますか？"
        description={`${suspension.target?.display_name ?? ''} のセッションは即時に失効し、ログインできなくなります。アカウントは削除されず、いつでも再開できます。`}
        confirmLabel="停止する"
        onConfirm={() => void suspension.confirm()}
        onClose={suspension.cancel}
      />

      <ConfirmDialog
        open={resetTarget !== null}
        title="パスワードを再設定しますか？"
        description={`${resetTarget?.display_name ?? ''} のセッションは即時に失効します。仮パスワードは発行直後に一度だけ表示され、閉じると二度と確認できません。`}
        confirmLabel="再設定する"
        onConfirm={() => void (resetTarget && resetPassword(resetTarget))}
        onClose={() => setResetTarget(null)}
      />

      <TemporaryPasswordModal
        open={issued !== null}
        temporaryPassword={issued?.password ?? ''}
        displayName={issued?.displayName ?? ''}
        onClose={() => setIssued(null)}
      />
    </>
  );
}
