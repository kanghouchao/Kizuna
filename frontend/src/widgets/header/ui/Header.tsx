'use client';

import Link from 'next/link';
import { useAuth, useStoreContext } from '@/entities/user';
import { isStoreDomain, storePath } from '@/shared/lib';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/shared/ui';
import { BellIcon, BuildingStorefrontIcon, UserCircleIcon } from '@heroicons/react/24/outline';
import { ModeToggle } from './ModeToggle';

export function Header() {
  const { logout } = useAuth();

  // 現在店舗・授権店舗・切替は店舗コンテキスト（両コンソール layout に搭載）が一手に担う。
  // stores: null = 読み込み中、[] = 非表示（資格無し/0件）、非空 = 店舗切替を表示。
  const { stores, currentStoreId, switchStore } = useStoreContext();

  const currentStoreName = stores?.find(store => String(store.id) === currentStoreId)?.name;

  // アカウント設定リンクは店舗別ドメイン経由でも移設後の店舗ルートを指す。
  // currentStoreId が未確定（pathname 由来 id も cookie ヒントも無い）場合は
  // "/store/undefined/..." を組まず platform 側へ fallback する。
  const accountHref =
    isStoreDomain() && currentStoreId
      ? storePath(currentStoreId, '/settings/account')
      : '/platform/settings/account';

  return (
    <header className="h-16 bg-card border-b flex items-center justify-between px-8 sticky top-0 z-20">
      <div className="flex items-center">
        <h2 className="text-lg font-medium text-foreground">管理パネル</h2>
      </div>

      <div className="flex items-center space-x-6">
        <Button variant="ghost" size="icon" className="text-muted-foreground">
          <BellIcon className="size-6" />
        </Button>

        {/* ヘッダーは両コンソールの layout が搭載する唯一の共通殻なので、
            表示モード切替はここに置けば双方から届く。 */}
        <ModeToggle />

        <div className="h-8 w-px bg-border" />

        {stores && stores.length > 0 && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm">
                <BuildingStorefrontIcon className="size-5 text-muted-foreground" />
                <span>{currentStoreName || '店舗を選択'}</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" sideOffset={8} className="w-56">
              {stores.map(store => (
                <DropdownMenuItem key={store.id} onSelect={() => switchStore(store.id)}>
                  {store.name}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        )}

        <div className="flex items-center space-x-4">
          <div className="text-right hidden sm:block">
            <p className="text-sm font-semibold text-foreground">管理者</p>
            <p className="text-xs text-muted-foreground">Platform Admin</p>
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                aria-label="アカウントメニュー"
                className="text-muted-foreground hover:text-primary-strong"
              >
                <UserCircleIcon className="size-8" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" sideOffset={8} className="w-48">
              <DropdownMenuItem asChild>
                <Link href={accountHref}>アカウント設定</Link>
              </DropdownMenuItem>
              {/* 原語の destructive 変種は淡色地の上で既定の赤を使い明モードで 4.5 を割るため、
                  文字は -strong 側を指定する。 */}
              <DropdownMenuItem
                onSelect={logout}
                className="text-destructive-strong focus:bg-destructive/10 focus:text-destructive-strong"
              >
                ログアウト
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  );
}
