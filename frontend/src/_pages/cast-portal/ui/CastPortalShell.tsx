'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  CalendarDaysIcon,
  ClipboardDocumentListIcon,
  UserCircleIcon,
} from '@heroicons/react/24/outline';
import { platformAuthApi } from '@/entities/user';
import { redirectToLogin } from '@/shared/lib';

interface CastPortalShellProps {
  children: React.ReactNode;
}

const TABS = [
  { href: '/cast/schedule', label: 'スケジュール', icon: CalendarDaysIcon },
  { href: '/cast/requests', label: '希望提出', icon: ClipboardDocumentListIcon },
  { href: '/cast/account', label: 'アカウント', icon: UserCircleIcon },
] as const;

/**
 * キャストポータルの共通シェル。モバイル優先の下タブバー構成で、桌面向け Sidebar shell とは別系統。
 * mount 時に本人確認（GET /platform/me）を行い、CAST 以外（未認証含む）はログイン画面へ差し戻す
 * （fail-closed）。ルーティングはサーバ側の @PreAuthorize と独立した UI 側の防御線。
 */
export function CastPortalShell({ children }: CastPortalShellProps) {
  const pathname = usePathname();
  const [authorized, setAuthorized] = useState(false);

  useEffect(() => {
    let cancelled = false;
    platformAuthApi
      .me()
      .then(me => {
        if (cancelled) return;
        if (me.user_type !== 'CAST') {
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
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <p className="text-sm text-gray-500">読み込み中...</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <main className="flex-1 overflow-y-auto pb-16">{children}</main>
      <nav className="fixed inset-x-0 bottom-0 z-10 flex border-t border-gray-200 bg-white">
        {TABS.map(tab => {
          const active = pathname === tab.href || pathname?.startsWith(`${tab.href}/`);
          const Icon = tab.icon;
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={active ? 'page' : undefined}
              className={`flex flex-1 flex-col items-center gap-1 py-2 text-xs font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${
                active ? 'text-blue-600' : 'text-gray-600 hover:bg-gray-50'
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
