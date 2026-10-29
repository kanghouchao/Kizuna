'use client';

import Link from 'next/link';
import { useAuth, useMe } from '@/entities/user';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  RegionError,
} from '@/shared/ui';
import { BellIcon, CircleUserRoundIcon } from 'lucide-react';
import { ModeToggle } from './ModeToggle';

export function Header() {
  const { logout } = useAuth();

  // 表示名は token claim に無い（wire 契約は authorities / userType のみ）ため、
  // /me の共有 seam（管理画面シェルに搭載）から読む。アカウント設定ページの
  // プロフィール更新は同じ seam の setMe で差し替わるため、この表示も再取得なしで追随する。
  const { me, failure: meFailure, reload: reloadMe } = useMe();

  return (
    <header className="h-16 bg-card border-b flex items-center justify-between px-8 sticky top-0 z-20">
      <div className="flex items-center">
        <h2 className="text-lg font-medium text-foreground">管理パネル</h2>
      </div>

      <div className="flex items-center gap-6">
        <Button variant="ghost" size="icon" className="text-muted-foreground">
          <BellIcon className="size-6" />
        </Button>

        {/* ヘッダーは管理画面の全頁に共通なので、表示モード切替はここに置けば全頁から届く。 */}
        <ModeToggle />

        <div className="h-8 w-px bg-border" />

        <div className="flex items-center gap-4">
          <div className="text-right hidden sm:block">
            {meFailure !== null ? (
              <RegionError
                message="アカウント情報の取得に失敗しました"
                onRetry={() => void reloadMe()}
              />
            ) : (
              me !== null && (
                <>
                  {/* 表示名は 150 字まで許されるため、行の固有幅に上界を与える。 */}
                  <p className="max-w-40 truncate text-sm font-semibold text-foreground">
                    {me.display_name}
                  </p>
                  <p className="max-w-40 truncate text-xs text-muted-foreground">{me.email}</p>
                </>
              )
            )}
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="アカウントメニュー"
                  className="text-muted-foreground hover:text-primary-strong"
                />
              }
            >
              <CircleUserRoundIcon className="size-8" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" sideOffset={8} className="w-48">
              <DropdownMenuItem render={<Link href="/admin/settings/account" />}>
                アカウント設定
              </DropdownMenuItem>
              {/* 原語の destructive 変種は淡色地の上で既定の赤を使い明モードで 4.5 を割るため、
                  文字は -strong 側を指定する。 */}
              <DropdownMenuItem
                onClick={() => logout()}
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
