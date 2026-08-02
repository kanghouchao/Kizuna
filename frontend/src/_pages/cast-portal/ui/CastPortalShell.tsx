'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { CalendarDaysIcon, CircleUserRoundIcon, ClipboardListIcon } from 'lucide-react';
import { readTokenClaims, redirectToLogin } from '@/shared/lib';

interface CastPortalShellProps {
  children: React.ReactNode;
}

const TABS = [
  { href: '/cast/schedule', label: 'スケジュール', icon: CalendarDaysIcon },
  { href: '/cast/requests', label: '希望提出', icon: ClipboardListIcon },
  { href: '/cast/account', label: 'アカウント', icon: CircleUserRoundIcon },
] as const;

/**
 * キャストポータルの共通シェル。モバイル優先の下タブバー構成で、桌面向け Sidebar shell とは別系統。
 * mount 時に token claim の userType で本人確認し、CAST 以外（未認証・壊れた token 含む）は
 * ログイン画面へ差し戻す（fail-closed）。失効・偽造はここでは見えないが、最初の API 呼び出しの
 * サーバ検証（401）が最終防衛線。ルーティングはサーバ側の @PreAuthorize と独立した UI 側の防御線。
 */
export function CastPortalShell({ children }: CastPortalShellProps) {
  const pathname = usePathname();
  const [authorized, setAuthorized] = useState(false);

  useEffect(() => {
    if (readTokenClaims()?.userType !== 'CAST') {
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
      <main className="flex-1 overflow-y-auto pb-16">{children}</main>
      <nav className="fixed inset-x-0 bottom-0 z-10 flex border-t bg-card">
        {TABS.map(tab => {
          const active = pathname === tab.href || pathname?.startsWith(`${tab.href}/`);
          const Icon = tab.icon;
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={active ? 'page' : undefined}
              className={`flex flex-1 flex-col items-center gap-1 py-2 text-xs font-medium focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none ${
                active
                  ? 'text-primary-strong'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground'
              }`}
            >
              <Icon className="h-6 w-6" />
              {tab.label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
