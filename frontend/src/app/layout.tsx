import type { Metadata } from 'next';
import { Inter, Cormorant_Garamond } from 'next/font/google';
import './globals.css';
import { AuthProvider } from '@/entities/user';
import { ToastProvider } from '@/_app/providers';
import { ThemeProvider } from '@/shared/ui';

const inter = Inter({ subsets: ['latin'] });
const cormorant = Cormorant_Garamond({
  weight: ['300', '400', '600'],
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-cormorant',
});

export const metadata: Metadata = {
  title: 'Kizuna - マルチ店舗',
  description: 'Laravel と Next.js を基盤としたマルチ店舗型コンテンツ管理システム',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ja" suppressHydrationWarning>
      <head>
        <link rel="icon" href="/images/favicon.ico" />
      </head>
      <body className={`${inter.className} ${cormorant.variable}`}>
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
          // scaffold 段階の暫定強制。既存画面は shadcn token 未使用のまま
          // ダーク OS では UA 由来のフォーム/スクロールバーだけが暗転して崩れるため、
          // ライトに固定する。ダークモード有効化票(mode-toggle + ページ restyle 完了)で
          // 除去し、system 既定へ戻す。
          forcedTheme="light"
        >
          <AuthProvider>
            {children}
            <ToastProvider />
          </AuthProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
