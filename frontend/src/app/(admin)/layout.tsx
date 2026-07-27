import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import '../globals.css';
import { AuthProvider } from '@/entities/user';
import { ThemeScope, ToastProvider } from '@/_app/providers';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Kizuna - マルチ店舗',
  description: 'Laravel と Next.js を基盤としたマルチ店舗型コンテンツ管理システム',
};

/**
 * 管理コンソール（トークン層）の根 layout。公開側とは根 layout を分けているため、
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
        <ThemeScope>
          <AuthProvider>
            {children}
            <ToastProvider />
          </AuthProvider>
        </ThemeScope>
      </body>
    </html>
  );
}
