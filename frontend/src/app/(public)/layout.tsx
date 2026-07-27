import type { Metadata } from 'next';
import { Inter, Cormorant_Garamond } from 'next/font/google';
import '../globals.css';
import { ToastProvider } from '@/_app/providers';

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

/**
 * 公開店舗サイトと認証画面（ログイン・招待受諾）の根 layout。テーマ配線を一切持たないため、
 * この世界の <html> に class や color-scheme が付くことは構造的にない。
 * AuthProvider はコンソール側だけの関心事（useAuth の消費者が存在しない）なので置かない。
 */
export default function PublicRootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ja">
      <head>
        <link rel="icon" href="/images/favicon.ico" />
      </head>
      <body className={`${inter.className} ${cormorant.variable}`}>
        {children}
        <ToastProvider />
      </body>
    </html>
  );
}
