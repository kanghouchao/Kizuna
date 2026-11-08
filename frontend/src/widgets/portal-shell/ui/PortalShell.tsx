'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ChevronLeftIcon, type LucideIcon } from 'lucide-react';
import { cn } from '@/shared/lib';

/** 利用者層の段。会員は本文 18px・押せる領域 48px 以上、キャストは本文 16px・44px 以上。 */
export type PortalTier = 'cast' | 'member';

export interface PortalSegment {
  href: string;
  label: string;
}

export interface PortalTab {
  href: string;
  label: string;
  icon: LucideIcon;
  /** タブの中の切替。どの区分の画面でもタブは選択状態になる。 */
  segments?: readonly PortalSegment[];
}

/** タブの画面は属するタブの href を、下位画面は戻り先を持つ。 */
export type PortalScreen = { title: string; tab: string } | { title: string; back: string };

/** 末尾の / を除いたパス → 画面。動的な区切りは app のルートと同じ `[名前]` で書く。 */
export type PortalScreens = Readonly<Record<string, PortalScreen>>;

interface PortalShellProps {
  tier: PortalTier;
  tabs: readonly PortalTab[];
  screens: PortalScreens;
  /** タブの件数バッジ（タブの href → 件数）。0 以下は出さない。 */
  badges?: Readonly<Record<string, number>>;
  /** 題名帯の直下、内容の前に置く案内。 */
  notice?: React.ReactNode;
  children: React.ReactNode;
}

const TIERS = {
  cast: { body: 'text-base', target: 'min-h-11 min-w-11', tabLabel: 'text-xs' },
  member: { body: 'text-lg', target: 'min-h-12 min-w-12', tabLabel: 'text-sm' },
} as const;

function normalize(path: string | null): string {
  if (!path) return '';
  return path.length > 1 && path.endsWith('/') ? path.slice(0, -1) : path;
}

/** 表をパスで引く。完全一致を先に見て、無ければ `[名前]` を一つの区切りに当てる。 */
function findScreen(screens: PortalScreens, pathname: string): PortalScreen | undefined {
  if (screens[pathname]) return screens[pathname];
  const parts = pathname.split('/');
  const key = Object.keys(screens).find(candidate => {
    const pattern = candidate.split('/');
    return (
      pattern.length === parts.length &&
      pattern.every((part, i) => part === parts[i] || /^\[[^\]]+\]$/.test(part))
    );
  });
  return key === undefined ? undefined : screens[key];
}

/**
 * 本人ポータル（キャスト・会員）の共通の外枠。画面名・戻り先・所属タブは screens の表で引き、
 * 下位画面は戻る導線を出して下部タブを出さない。PC 幅でもスマートフォンの幅の列に収める。
 * 本人確認はしない — 未ログインは最初の API の 401 経路が、別種別はサーバの 403 が差し戻す。
 */
export function PortalShell({ tier, tabs, screens, badges, notice, children }: PortalShellProps) {
  const pathname = normalize(usePathname());
  const current = findScreen(screens, pathname);
  const back = current && 'back' in current ? current.back : null;
  const activeTab = current && 'tab' in current ? current.tab : null;
  const segments = tabs.find(tab => tab.href === activeTab)?.segments;
  const size = TIERS[tier];

  return (
    <div className="min-h-dvh bg-muted">
      <div
        className={cn(
          'mx-auto flex min-h-dvh w-full max-w-md flex-col bg-background text-foreground',
          size.body
        )}
      >
        <header className="sticky top-0 z-10 border-b bg-background pt-[env(safe-area-inset-top)]">
          <div className={cn('flex items-center gap-1 px-2', size.target)}>
            {back !== null && (
              <Link
                href={back}
                aria-label="戻る"
                className={cn(
                  'flex items-center justify-center rounded-md text-foreground hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none',
                  size.target
                )}
              >
                <ChevronLeftIcon className="size-6" aria-hidden="true" />
              </Link>
            )}
            <h1 className={cn('truncate text-lg font-semibold', back === null && 'px-2')}>
              {current?.title}
            </h1>
          </div>
          {segments && (
            <nav aria-label={`${current?.title}の切替`} className="px-4 pb-3">
              <div className="flex rounded-lg bg-muted p-1">
                {segments.map(segment => {
                  const selected = normalize(segment.href) === pathname;
                  return (
                    <Link
                      key={segment.href}
                      href={segment.href}
                      aria-current={selected ? 'page' : undefined}
                      className={cn(
                        'flex flex-1 items-center justify-center rounded-md px-3 text-sm font-medium focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none',
                        size.target,
                        selected
                          ? 'bg-card text-foreground shadow-sm'
                          : 'text-foreground hover:bg-card/60'
                      )}
                    >
                      {segment.label}
                    </Link>
                  );
                })}
              </div>
            </nav>
          )}
        </header>
        {notice}
        <main
          className={cn(
            'flex-1 p-4',
            back === null
              ? 'pb-[calc(5rem_+_env(safe-area-inset-bottom))]'
              : 'pb-[calc(1rem_+_env(safe-area-inset-bottom))]'
          )}
        >
          {children}
        </main>
        {back === null && (
          <nav
            aria-label="メニュー"
            className="fixed inset-x-0 bottom-0 z-10 mx-auto flex w-full max-w-md border-t bg-card pb-[env(safe-area-inset-bottom)]"
          >
            {tabs.map(tab => {
              const active = tab.href === activeTab;
              const count = badges?.[tab.href] ?? 0;
              const Icon = tab.icon;
              return (
                <Link
                  key={tab.href}
                  href={tab.href}
                  aria-current={active ? 'page' : undefined}
                  aria-label={count > 0 ? `${tab.label} ${count}件` : undefined}
                  className={cn(
                    'flex h-16 flex-1 flex-col items-center justify-center gap-1 font-medium focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none focus-visible:ring-inset',
                    size.tabLabel,
                    active
                      ? 'text-primary-strong'
                      : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                  )}
                >
                  <span className="relative">
                    <Icon className="size-6" aria-hidden="true" />
                    {count > 0 && (
                      <span
                        aria-hidden="true"
                        className="absolute -top-1.5 -right-2.5 min-w-5 rounded-full bg-destructive px-1 text-center text-xs leading-5 text-destructive-foreground"
                      >
                        {count}
                      </span>
                    )}
                  </span>
                  {tab.label}
                </Link>
              );
            })}
          </nav>
        )}
      </div>
    </div>
  );
}
