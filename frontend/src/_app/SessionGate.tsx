'use client';

import { useCallback, useEffect, useState, useSyncExternalStore } from 'react';
import {
  clearSessionToken,
  getSessionAlert,
  restoreSession,
  subscribeLogout,
  setSessionAlert,
  subscribeSessionAlert,
} from '@/shared/api';
import { clearSession, redirectToLogin, rememberMemberReturnPath } from '@/shared/lib';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  ConfirmDialog,
  RegionError,
} from '@/shared/ui';

/**
 * ログイン後の画面（管理画面・キャスト・会員）の入口。開いたときにリフレッシュでアクセストークンを得てから
 * 中身を描くので、各シェルの GET /me はトークンを持って出る。失敗の差し戻しと表示は restoreSession が受け持つ。
 */
export function SessionGate({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<'pending' | 'ready' | 'error'>('pending');

  const restore = useCallback(() => {
    void restoreSession().then(result => {
      if (result.status === 'ok') setState('ready');
      if (result.status === 'error') setState('error');
    });
  }, []);

  useEffect(restore, [restore]);

  // 同じ利用者の他のタブのログアウトに従う。別の利用者のタブは巻き込まない（照合は subscribeLogout）。
  useEffect(
    () =>
      subscribeLogout(() => {
        clearSessionToken();
        redirectToLogin();
      }),
    []
  );

  return (
    <>
      {state === 'ready' && children}
      {state === 'pending' && <p className="p-8 text-sm text-muted-foreground">読み込み中...</p>}
      {state === 'error' && (
        <div className="p-8">
          <RegionError
            message="ログイン状態を確認できませんでした"
            onRetry={() => {
              setState('pending');
              restore();
            }}
          />
        </div>
      )}
      <SessionAlertDialog />
    </>
  );
}

function SessionAlertDialog() {
  const alert = useSyncExternalStore(subscribeSessionAlert, getSessionAlert, () => null);

  if (alert === 'credential_changed') {
    return (
      <AlertDialog open>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>再ログインが必要です</AlertDialogTitle>
            <AlertDialogDescription>
              権限やパスワードが変更されました。もう一度ログインしてください
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogAction
              onClick={() => {
                clearSession();
                setSessionAlert(null);
                // 401 で差し戻す経路と同じく、会員ポータルの現在地（伝票のフラグメントを含む）を預ける。
                rememberMemberReturnPath(
                  `${window.location.pathname}${window.location.search}`,
                  window.location.hash
                );
                redirectToLogin();
              }}
            >
              ログイン画面へ
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    );
  }

  return (
    <ConfirmDialog
      open={alert === 'conflict'}
      title="別のアカウントでログインされています"
      description="このブラウザの別のタブで、別のアカウントにログインしました。再読み込みすると、そのアカウントで表示します。入力中の内容は再読み込みで失われます。"
      confirmLabel="再読み込み"
      onConfirm={() => window.location.reload()}
      onClose={() => setSessionAlert(null)}
    />
  );
}
