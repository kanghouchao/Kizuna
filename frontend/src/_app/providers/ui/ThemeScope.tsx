'use client';

import { usePathname } from 'next/navigation';
import { ThemeProvider } from '@/shared/ui';

/** 管理コンソールのパス。店舗ドメインはこの外側で公開店舗サイトを配信する。 */
const CONSOLE_PREFIXES = ['/platform', '/store'];

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
  const inConsole = CONSOLE_PREFIXES.some(prefix => pathname?.startsWith(prefix));

  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
      forcedTheme={inConsole ? undefined : 'light'}
    >
      {children}
    </ThemeProvider>
  );
}
