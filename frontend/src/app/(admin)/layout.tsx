import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import '../globals.css';
import { AuthProvider } from '@/entities/user';
import { SessionGate, ThemeScope, ToastProvider } from '@/_app';
import { AppDomainScope } from '@/_app/index.server';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Kizuna - マルチ店舗',
  description: '単一グループの複数店舗を運営する CMS / CRM / HRM',
};

/**
 * ログイン後の画面（従業員・キャスト・会員）の根 layout。SessionGate が文書ごとに 1 回リフレッシュしてから
 * 各シェルを描く。公開側とは根 layout を分けているため、
 * 世界を跨ぐ遷移はフルページ読み込みになり、next-themes のブロッキング script が必ず先に走る。
 * テーマ配線（ThemeScope）はこの世界だけが持つ。
 */
export default function AdminRootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ja" suppressHydrationWarning>
      <head>
        <link rel="icon" href="/images/favicon.ico" />
      </head>
      <body className={inter.className}>
        <AppDomainScope>
          <ThemeScope>
            <AuthProvider>
              <SessionGate>{children}</SessionGate>
              <ToastProvider />
            </AuthProvider>
          </ThemeScope>
        </AppDomainScope>
      </body>
    </html>
  );
}
