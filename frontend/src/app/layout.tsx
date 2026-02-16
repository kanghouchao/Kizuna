import type { Metadata } from 'next';
import { Inter, Cormorant_Garamond } from 'next/font/google';
import './globals.css';
import { AuthProvider } from '@/contexts/AuthContext';
import { ToastProvider } from '@/components/ToastProvider';

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
    <html lang="ja">
      <head>
        <link rel="icon" href="/images/favicon.ico" />
      </head>
      <body className={`${inter.className} ${cormorant.variable}`}>
        <AuthProvider>
          {children}
          <ToastProvider />
        </AuthProvider>
      </body>
    </html>
  );
}
