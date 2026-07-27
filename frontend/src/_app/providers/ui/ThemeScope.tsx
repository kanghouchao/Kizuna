'use client';

import { usePathname } from 'next/navigation';
import { ThemeProvider } from '@/shared/ui';
import { isTokenThemedPath } from '@/shared/lib';

/**
 * テーマは管理コンソールだけの関心事。公開店舗サイトと同一 origin で配信されるため、
 * provider をただ根へ置くと <html> の class と color-scheme が公開サイトにも及ぶ。
 *
 * provider の設置場所で囲い込むだけでは足りない。next-themes は適用した class と
 * color-scheme をアンマウント時に取り除かないため、コンソールから外へ遷移すると
 * 直前のテーマが <html> に残る。そこで単一の provider を根に置いたまま、コンソール
 * 外では light を強制して現在地に追随させる。
 */
export function ThemeScope({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const themed = isTokenThemedPath(pathname);

  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
      forcedTheme={themed ? undefined : 'light'}
    >
      {children}
    </ThemeProvider>
  );
}
