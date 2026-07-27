'use client';

import { useTheme } from 'next-themes';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from '@/shared/ui';
import { MoonIcon, SunIcon } from '@heroicons/react/24/outline';

export function ModeToggle() {
  // トリガーの絵柄は現在値を読まず dark: 変種、すなわち適用済みの .dark の有無だけで決める。
  // 読むとサーバ描画（保存値を知らない）と初回フレームが食い違い、hydration 不一致か
  // mounted ガード由来のちらつきを招くため。
  //
  // 一方メニューの中身は開いている間しか描かれず、クライアント描画しか通らないので現在値を
  // 読んでよい。system は、OS がたまたま同じ見た目を返しているときの light / dark と絵柄では
  // 区別できない。選択状態を radio として出し、aria-checked にも載せる。
  const { theme, setTheme } = useTheme();

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
        <DropdownMenuRadioGroup value={theme} onValueChange={setTheme}>
          <DropdownMenuRadioItem value="light">ライト</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="dark">ダーク</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="system">システム</DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
