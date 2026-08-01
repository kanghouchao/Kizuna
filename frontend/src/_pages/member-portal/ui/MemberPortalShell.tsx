'use client';

import { useEffect, useState } from 'react';
import { platformAuthApi } from '@/entities/user';
import { redirectToLogin } from '@/shared/lib';

interface MemberPortalShellProps {
  children: React.ReactNode;
}

/**
 * 会員ポータルの共通シェル。モバイル優先の単画面構成（現状はホームのみ）で、桌面向け Sidebar shell とは別系統。
 * mount 時に本人確認（GET /platform/me）を行い、MEMBER 以外（未認証含む）はログイン画面へ差し戻す
 * （fail-closed）。ルーティングはサーバ側の @PreAuthorize と独立した UI 側の防御線。
 */
export function MemberPortalShell({ children }: MemberPortalShellProps) {
  const [authorized, setAuthorized] = useState(false);

  useEffect(() => {
    let cancelled = false;
    platformAuthApi
      .me()
      .then(me => {
        if (cancelled) return;
        if (me.user_type !== 'MEMBER') {
          redirectToLogin();
          return;
        }
        setAuthorized(true);
      })
      .catch(() => {
        if (!cancelled) redirectToLogin();
      });
    return () => {
      cancelled = true;
    };
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
