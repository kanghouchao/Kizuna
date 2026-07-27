'use client';

import { StoreContextProvider } from '@/entities/user';
import { Sidebar } from '@/widgets/sidebar';
import { Header } from '@/widgets/header';
import { ThemeProvider } from '@/shared/ui';

export default function StoreLayout({ children }: { children: React.ReactNode }) {
  return (
    // テーマは管理コンソールだけの関心事。公開店舗サイトと同一 origin で配信されるため、
    // provider を根 layout に置くと storefront にも class と color-scheme が及んでしまう。
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      <StoreContextProvider>
        <div className="flex h-screen bg-background overflow-hidden">
          {/* Sidebar Component */}
          <Sidebar />

          {/* Main Container */}
          <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
            {/* Header Component */}
            <Header />

            {/* Main Content Area */}
            {/* relative: 絶対配置の子（Radix Select が描画するフォーム互換用の隠し select 等）の
              包含ブロックをこのスクロール領域に閉じ込め、ページ全体が伸びるのを防ぐ */}
            <main className="relative flex-1 overflow-y-auto p-8 custom-scrollbar">
              <div className="max-w-7xl mx-auto">{children}</div>
            </main>
          </div>
        </div>
      </StoreContextProvider>
    </ThemeProvider>
  );
}
