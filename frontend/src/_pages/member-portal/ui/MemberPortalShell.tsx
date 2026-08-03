'use client';

import { useEffect, useState } from 'react';
import { readTokenClaims, redirectToLogin, rememberMemberReturnPath } from '@/shared/lib';

interface MemberPortalShellProps {
  children: React.ReactNode;
}

/**
 * 会員ポータルの共通シェル。モバイル優先の単画面構成（現状はホームのみ）で、桌面向け Sidebar shell とは別系統。
 * mount 時に token claim の userType で本人確認し、MEMBER 以外（未認証・壊れた token 含む）は
 * ログイン画面へ差し戻す（fail-closed）。失効・偽造はここでは見えないが、最初の API 呼び出しの
 * サーバ検証（401）が最終防衛線。ルーティングはサーバ側の @PreAuthorize と独立した UI 側の防御線。
 */
export function MemberPortalShell({ children }: MemberPortalShellProps) {
  const [authorized, setAuthorized] = useState(false);

  useEffect(() => {
    if (readTokenClaims()?.userType !== 'MEMBER') {
      // 公式サイトの予約導線から未ログインで来た場合、ログイン後に元の画面（店舗つき）へ戻せるようにする。
      rememberMemberReturnPath(`${window.location.pathname}${window.location.search}`);
      redirectToLogin();
      return;
    }
    setAuthorized(true);
  }, []);

  if (!authorized) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <p className="text-sm text-muted-foreground">読み込み中...</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <main className="flex-1 overflow-y-auto">{children}</main>
    </div>
  );
}
