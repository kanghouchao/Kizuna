'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  CalendarDaysIcon,
  CircleUserRoundIcon,
  ClipboardListIcon,
  ListChecksIcon,
  ReceiptTextIcon,
} from 'lucide-react';

interface CastPortalShellProps {
  children: React.ReactNode;
}

const TABS = [
  { href: '/cast/schedule', label: 'スケジュール', icon: CalendarDaysIcon },
  { href: '/cast/requests', label: '希望提出', icon: ClipboardListIcon },
  { href: '/cast/service-conditions', label: 'サービス条件', icon: ListChecksIcon },
  { href: '/cast/remunerations', label: '報酬明細', icon: ReceiptTextIcon },
  { href: '/cast/account', label: 'アカウント', icon: CircleUserRoundIcon },
] as const;

/**
 * キャストポータルの共通シェル。モバイル優先の下タブバー構成で、桌面向け Sidebar shell とは別系統。
 * 本人確認はしない — 未ログインは各画面の最初の API の 401 が全域の 401 経路でログインへ差し戻し、
 * 別種別のセッションはサーバの認可が 403 で拒む。
 */
export function CastPortalShell({ children }: CastPortalShellProps) {
  const pathname = usePathname();

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
