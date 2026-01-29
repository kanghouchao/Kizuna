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
  FaceSmileIcon,
  ClockIcon,
  UsersIcon,
  MegaphoneIcon,
  DocumentTextIcon,
  PhotoIcon,
  BuildingStorefrontIcon,
  KeyIcon,
} from '@heroicons/react/24/outline';
import { centralApi } from '@/services/central/api';
import { tenantApi } from '@/services/tenant/api';
import { MenuVO } from '@/types/api';

const ICON_MAP: { [key: string]: React.ForwardRefExoticComponent<any> } = {
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
  FaceSmileIcon,
  ClockIcon,
  UsersIcon,
  MegaphoneIcon,
  DocumentTextIcon,
  PhotoIcon,
  BuildingStorefrontIcon,
  KeyIcon,
};

export function Sidebar() {
  const pathname = usePathname();
  const [role, setRole] = useState<string>('central');
  const [navigation, setNavigation] = useState<any[]>([]);

  useEffect(() => {
    // Read the role from the cookie set by middleware
    const mwRole = Cookies.get('x-mw-role');
    if (mwRole) {
      setRole(mwRole);
    }
  }, []);

  useEffect(() => {
    const fetchMenus = async () => {
      try {
        let menus: MenuVO[] = [];
        if (role === 'central') {
          menus = await centralApi.getMenus();
        } else if (role === 'tenant') {
          menus = await tenantApi.getMenus();
        }

        const mappedNavigation = menus.map(section => ({
          name: section.name,
          items: (section.items || []).map(item => ({
            name: item.name,
            href: item.path || '#',
            icon: item.icon && ICON_MAP[item.icon] ? ICON_MAP[item.icon] : HomeIcon,
          })),
        }));

        setNavigation(mappedNavigation);
      } catch (error) {
        console.error('Failed to fetch menus', error);
      }
    };

    fetchMenus();
  }, [role]);

  return (
    <aside className="w-64 bg-slate-800 text-white shrink-0 hidden md:block border-r border-slate-700">
      <div className="h-16 flex items-center px-6 border-b border-slate-700">
        <span className="text-xl font-bold tracking-wider text-indigo-400">
          {role === 'tenant' ? 'TENANT' : 'CENTRAL'}
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
                {section.items.map((item: any) => {
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
