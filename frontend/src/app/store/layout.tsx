'use client';

import { StoreContextProvider } from '@/entities/user';
import { Sidebar } from '@/widgets/sidebar';
import { Header } from '@/widgets/header';

export default function StoreLayout({ children }: { children: React.ReactNode }) {
  return (
    <StoreContextProvider>
      <div className="flex h-screen bg-gray-50 overflow-hidden">
        {/* Sidebar Component */}
        <Sidebar />

        {/* Main Container */}
        <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
          {/* Header Component */}
          <Header />

          {/* Main Content Area */}
          <main className="flex-1 overflow-y-auto p-8 custom-scrollbar">
            <div className="max-w-7xl mx-auto">{children}</div>
          </main>
        </div>
      </div>
    </StoreContextProvider>
  );
}
