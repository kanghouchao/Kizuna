'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  BotIcon,
  BriefcaseIcon,
  BuildingIcon,
  ChartColumnIcon,
  CirclePlusIcon,
  ClipboardListIcon,
  ClockIcon,
  FileTextIcon,
  HouseIcon,
  ImageIcon,
  JapaneseYenIcon,
  KeyRoundIcon,
  MegaphoneIcon,
  SettingsIcon,
  ShieldAlertIcon,
  SlidersHorizontalIcon,
  SmileIcon,
  StoreIcon,
  UserCogIcon,
  UsersIcon,
  UsersRoundIcon,
  type LucideIcon,
} from 'lucide-react';
import { menuApi, MenuVO } from '@/entities/menu';

// キーはメニュー API（seed データ）が返す icon 文字列との wire 契約。
// lucide-react のエクスポート名をそのまま用い、メニューが参照してよいアイコンの許可リストを兼ねる。
const ICON_MAP: Record<string, LucideIcon> = {
  BotIcon,
  BriefcaseIcon,
  BuildingIcon,
  ChartColumnIcon,
  CirclePlusIcon,
  ClipboardListIcon,
  ClockIcon,
  FileTextIcon,
  HouseIcon,
  ImageIcon,
  JapaneseYenIcon,
  KeyRoundIcon,
  MegaphoneIcon,
  SettingsIcon,
  ShieldAlertIcon,
  SlidersHorizontalIcon,
  SmileIcon,
  StoreIcon,
  UserCogIcon,
  UsersIcon,
  UsersRoundIcon,
};

interface NavigationItem {
  name?: string;
  href: string;
  icon: LucideIcon;
}

interface NavigationSection {
  name?: string;
  // 自身が path を持つ根は見出しではなく単独のリンクとして描く。
  href?: string;
  icon: LucideIcon;
  items: NavigationItem[];
}

function resolveIcon(icon?: string): LucideIcon {
  return icon && ICON_MAP[icon] ? ICON_MAP[icon] : HouseIcon;
}

function NavLink({ item, isActive }: { item: NavigationItem; isActive: boolean }) {
  return (
    <Link
      href={item.href}
      className={`relative flex items-center px-3 py-2.5 text-sm font-medium rounded-lg transition-colors duration-200 ${
        isActive
          ? 'bg-primary/10 text-primary-strong'
          : 'text-muted-foreground hover:bg-muted hover:text-foreground'
      }`}
    >
      {/* 現在地を示す 2px のエッジバー。文字を持たないので
          リンクのアクセシブル名には寄与しない */}
      {isActive && (
        <span
          aria-hidden
          className="absolute inset-y-1 left-0 w-0.5 rounded-full bg-primary-strong"
        />
      )}
      {/* アイコンは currentColor を継承し、hover / 現在地でラベルと一緒に色が動く */}
      <item.icon className="mr-3 h-5 w-5 shrink-0" />
      {item.name}
    </Link>
  );
}

export function Sidebar() {
  const pathname = usePathname();
  const [navigation, setNavigation] = useState<NavigationSection[]>([]);

  useEffect(() => {
    const fetchMenus = async () => {
      try {
        const menus: MenuVO[] = await menuApi.getMenus();

        const mappedNavigation = menus.map(section => ({
          name: section.name,
          href: section.path,
          icon: resolveIcon(section.icon),
          items: (section.items || []).map(item => ({
            name: item.name,
            href: item.path || '#',
            icon: resolveIcon(item.icon),
          })),
        }));

        setNavigation(mappedNavigation);
      } catch (error) {
        console.error('Failed to fetch menus', error);
        // メニュー取得に失敗したら最低限の導線（全員の着地先）に落とす。
        setNavigation([
          { name: 'ダッシュボード', href: '/admin/dashboard', icon: HouseIcon, items: [] },
        ]);
      }
    };

    fetchMenus();
  }, []);

  return (
    <aside className="w-64 bg-card shrink-0 hidden md:block border-r">
      <div className="h-16 flex items-center px-6 border-b">
        <span className="text-xl font-bold tracking-wider text-primary-strong">Kizuna</span>
      </div>
      <div className="p-4 overflow-y-auto h-[calc(100vh-4rem)]">
        <nav className="space-y-8">
          {navigation.map(section =>
            section.href ? (
              <NavLink
                key={section.name}
                item={{ name: section.name, href: section.href, icon: section.icon }}
                isActive={pathname === section.href}
              />
            ) : (
              <div key={section.name}>
                <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-widest mb-4 px-3">
                  {section.name}
                </h3>
                <ul className="space-y-1">
                  {section.items.map(item => (
                    <li key={item.name}>
                      <NavLink item={item} isActive={pathname === item.href} />
                    </li>
                  ))}
                </ul>
              </div>
            )
          )}
        </nav>
      </div>
    </aside>
  );
}
