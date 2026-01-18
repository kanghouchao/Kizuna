'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import Cookies from 'js-cookie';
import { useEffect, useState } from 'react';
import {
  HomeIcon,
  BuildingOfficeIcon,
  ClipboardDocumentListIcon,
  PlusCircleIcon,
  CurrencyYenIcon,
  BriefcaseIcon,
  ChartBarIcon,
  UserGroupIcon,
  CogIcon,
  AdjustmentsHorizontalIcon,
} from '@heroicons/react/24/outline';

const centralNavigation = [
  {
    name: 'メイン',
    items: [
      { name: 'ダッシュボード', href: '/central/dashboard', icon: HomeIcon },
      { name: 'テナント一覧', href: '/central/tenants', icon: BuildingOfficeIcon },
    ],
  },
];

const tenantNavigation = [
  {
    name: 'メイン',
    items: [{ name: 'ダッシュボード', href: '/tenant/dashboard', icon: HomeIcon }],
  },
  {
    name: '配車システム',
    items: [
      { name: 'オーダー一覧', href: '/tenant/orders', icon: ClipboardDocumentListIcon },
      { name: 'オーダー登録', href: '/tenant/orders/create', icon: PlusCircleIcon },
      { name: '精算', href: '#', icon: CurrencyYenIcon },
      { name: '業務', href: '#', icon: BriefcaseIcon },
      { name: '集計', href: '#', icon: ChartBarIcon },
      { name: '顧客管理', href: '#', icon: UserGroupIcon },
      { name: 'マスタ管理', href: '#', icon: CogIcon },
      { name: '設定', href: '#', icon: AdjustmentsHorizontalIcon },
    ],
  },
];

export function Sidebar() {
  const pathname = usePathname();
  const [role, setRole] = useState<string>('central');

  useEffect(() => {
    // Read the role from the cookie set by middleware
    const mwRole = Cookies.get('x-mw-role');
    if (mwRole) {
      setRole(mwRole);
    }
  }, []);

  const navigation = role === 'tenant' ? tenantNavigation : centralNavigation;

  return (
    <aside className="w-64 bg-slate-800 text-white shrink-0 hidden md:block border-r border-slate-700">
      <div className="h-16 flex items-center px-6 border-b border-slate-700">
        <span className="text-xl font-bold tracking-wider text-indigo-400">
          {role === 'tenant' ? 'TENANT' : 'OLI-CMS'}
        </span>
      </div>
      <div className="p-4 overflow-y-auto h-[calc(100vh-4rem)] custom-scrollbar">
        <nav className="space-y-8">
          {navigation.map(section => (
            <div key={section.name}>
              <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-widest mb-4 px-3">
                {section.name}
              </h3>
              <ul className="space-y-1">
                {section.items.map(item => {
                  const isActive = pathname === item.href;
                  return (
                    <li key={item.name}>
                      <Link
                        href={item.href}
                        className={`flex items-center px-3 py-2.5 text-sm font-medium rounded-lg transition-all duration-200 group ${
                          isActive
                            ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-900/20'
                            : 'text-slate-400 hover:bg-slate-700/50 hover:text-white'
                        }`}
                      >
                        <item.icon
                          className={`mr-3 h-5 w-5 shrink-0 ${
                            isActive ? 'text-white' : 'text-slate-500 group-hover:text-slate-300'
                          }`}
                        />
                        {item.name}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </div>
    </aside>
  );
}
