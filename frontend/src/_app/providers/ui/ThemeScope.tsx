'use client';

import { ThemeProvider } from '@/shared/ui';

/**
 * テーマは管理コンソールだけの関心事。作用範囲は根 layout の route group 分割で
 * 構造的に隔離される（(admin) の根 layout だけがこれを設置し、公開側の根 layout は
 * テーマ配線を持たない）ため、ここでは現在地による適用条件を持たない。
 */
export function ThemeScope({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      {children}
    </ThemeProvider>
  );
}
