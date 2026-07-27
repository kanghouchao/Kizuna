'use client';

import { useTheme } from 'next-themes';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/shared/ui';
import { MoonIcon, SunIcon } from '@heroicons/react/24/outline';

export function ModeToggle() {
  // 現在値は読まない。読むとサーバ描画（保存値を知らない）と初回フレームが食い違い、
  // hydration 不一致か mounted ガード由来のちらつきを招く。表示は下の dark: 変種、
  // すなわち適用済みの .dark の有無だけで決まるので、初回フレームから正しい。
  const { setTheme } = useTheme();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          aria-label="表示モード"
          className="text-muted-foreground"
        >
          <SunIcon className="size-6 dark:hidden" />
          <MoonIcon className="hidden size-6 dark:block" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" sideOffset={8} className="w-40">
        <DropdownMenuItem onSelect={() => setTheme('light')}>ライト</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => setTheme('dark')}>ダーク</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => setTheme('system')}>システム</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
