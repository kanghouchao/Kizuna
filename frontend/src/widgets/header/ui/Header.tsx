'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Menu, MenuButton, MenuItem, MenuItems } from '@headlessui/react';
import { platformAuthApi, PlatformStore, useAuth } from '@/entities/user';
import {
  getPlatformConsole,
  getPlatformStoreId,
  isPlatformSession,
  isStoreConsole,
  isTenantDomain,
  setPlatformStore,
} from '@/shared/lib';
import { BellIcon, BuildingStorefrontIcon, UserCircleIcon } from '@heroicons/react/24/outline';

export function Header() {
  const { logout } = useAuth();
  const accountHref = isTenantDomain() ? '/store/settings/account' : '/platform/settings/account';

  const [showStoreSwitch, setShowStoreSwitch] = useState(false);
  const [stores, setStores] = useState<PlatformStore[]>([]);
  const [currentStoreId, setCurrentStoreId] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (isPlatformSession() && isStoreConsole(getPlatformConsole())) {
      setShowStoreSwitch(true);
      setCurrentStoreId(getPlatformStoreId());
      platformAuthApi
        .stores()
        .then(setStores)
        .catch(error => console.error('Failed to fetch stores', error));
    }
  }, []);

  const currentStoreName = stores.find(store => String(store.id) === currentStoreId)?.name;

  const handleStoreSelect = (id: number) => {
    if (String(id) !== currentStoreId) {
      setPlatformStore(id);
      window.location.reload();
    }
  };

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

        {showStoreSwitch && (
          <Menu as="div" className="relative">
            <MenuButton
              disabled={stores.length === 0}
              className="flex items-center gap-2 rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <BuildingStorefrontIcon className="h-5 w-5 text-gray-400" />
              <span>{currentStoreName || '店舗を選択'}</span>
            </MenuButton>
            <MenuItems className="absolute right-0 mt-2 w-56 bg-white rounded-md shadow-lg py-1 border border-gray-100 focus:outline-none">
              {stores.map(store => (
                <MenuItem key={store.id}>
                  <button
                    onClick={() => handleStoreSelect(store.id)}
                    className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 focus:bg-gray-50 focus:outline-none"
                  >
                    {store.name}
                  </button>
                </MenuItem>
              ))}
            </MenuItems>
          </Menu>
        )}

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
              <Link
                href={accountHref}
                className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
              >
                アカウント設定
              </Link>
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
