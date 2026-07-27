'use client';

import { StoreContextProvider } from '@/entities/user';
import { Sidebar } from '@/widgets/sidebar';
import { Header } from '@/widgets/header';

export default function StoreLayout({ children }: { children: React.ReactNode }) {
  return (
    <StoreContextProvider>
      {/* 管理コンソールは下限幅を持つ。これより狭い窓では横スクロールへ倒し、
          はみ出した操作を切り落とさない（切り落とすと到達手段が消える）。 */}
      <div className="flex h-screen bg-background overflow-x-auto">
        {/* Sidebar Component */}
        <Sidebar />

        {/* Main Container */}
        <div className="flex-1 flex flex-col min-w-[40rem] overflow-hidden">
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
  );
}
