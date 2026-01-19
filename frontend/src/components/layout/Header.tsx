'use client';

import { useAuth } from '@/contexts/AuthContext';
import { BellIcon, UserCircleIcon } from '@heroicons/react/24/outline';

export function Header() {
  const { logout } = useAuth();

  return (
    <header className="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-8 sticky top-0 z-20">
      <div className="flex items-center">
        <h2 className="text-lg font-medium text-gray-800">管理パネル</h2>
      </div>

      <div className="flex items-center space-x-6">
        <button className="text-gray-400 hover:text-gray-600 transition-colors">
          <BellIcon className="h-6 w-6" />
        </button>

        <div className="h-8 w-px bg-gray-200" />

        <div className="flex items-center space-x-4">
          <div className="text-right hidden sm:block">
            <p className="text-sm font-semibold text-gray-900">管理者</p>
            <p className="text-xs text-gray-500">Central Admin</p>
          </div>
          <div className="relative group">
            {' '}
            <button className="flex items-center focus:outline-none">
              <UserCircleIcon className="h-8 w-8 text-gray-400 group-hover:text-indigo-500 transition-colors" />
            </button>
            <div className="absolute right-0 mt-2 w-48 bg-white rounded-md shadow-lg py-1 border border-gray-100 hidden group-hover:block transition-all duration-200 opacity-0 group-hover:opacity-100 scale-95 group-hover:scale-100 origin-top-right">
              <button
                onClick={logout}
                className="block w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50"
              >
                ログアウト
              </button>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
