'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import Cookies from 'js-cookie';
import { useEffect, useState } from 'react';
import {
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
} from 'lucide-react';
import { menuApi, MenuVO } from '@/entities/menu';
import {
  getPlatformConsole,
  getPlatformStoreId,
  getStoreIdFromPath,
  isStoreConsole,
  resolveStoreHref,
  storeEntryPath,
} from '@/shared/lib';

// キーはメニュー API（seed データ）が返す icon 文字列との wire 契約。
// lucide-react のエクスポート名をそのまま用い、メニューが参照してよいアイコンの許可リストを兼ねる。
const ICON_MAP: { [key: string]: React.ForwardRefExoticComponent<any> } = {
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

export function Sidebar() {
  const pathname = usePathname();
  // 店舗リンクへ埋め込む storeId は path 由来 id を最優先し、無ければ前回選択 cookie に fallback する。
  // path 組立は store-route（店舗パス知識の唯一 module）の resolveStoreHref に委ねる。
  const storeId = getStoreIdFromPath(pathname) ?? getPlatformStoreId();
  const [role, setRole] = useState<string>('platform');
  const [navigation, setNavigation] = useState<any[]>([]);

  useEffect(() => {
    // 「前回選択」cookie の更新は apiClient の応答インターセプタ（X-Store-ID 受理時のみ）に委ね、
    // 未検証の URL 由来 id をここで書き込まない（非授権 id の手打ちで cookie を汚染しない）。
    // 平台セッションがあれば優先する（コンソール値: platform / store）
    const platformConsole = getPlatformConsole();
    if (platformConsole) {
      setRole(isStoreConsole(platformConsole) ? 'store' : 'platform');
      return;
    }
    // なければ既存どおり middleware が設定した cookie から読む
    const mwRole = Cookies.get('x-mw-role');
    if (mwRole) {
      setRole(mwRole);
    }
  }, []);

  useEffect(() => {
    const fetchMenus = async () => {
      try {
        const menus: MenuVO[] = await menuApi.getMenus();

        const mappedNavigation = menus.map(section => ({
          name: section.name,
          items: (section.items || []).map(item => ({
            name: item.name,
            href: item.path || '#',
            icon: item.icon && ICON_MAP[item.icon] ? ICON_MAP[item.icon] : HouseIcon,
          })),
        }));

        setNavigation(mappedNavigation);
      } catch (error) {
        console.error('Failed to fetch menus', error);
        // メニュー取得に失敗したら最低限の導線に落とす。
        // role state ではなく role effect と同じ解決順（platform console → x-mw-role cookie）で直接判定する。
        const platformConsole = getPlatformConsole();
        const isStore = platformConsole
          ? isStoreConsole(platformConsole)
          : Cookies.get('x-mw-role') === 'store';
        setNavigation([
          {
            name: 'メイン',
            items: [
              {
                name: isStore ? '店舗コンソール' : 'ダッシュボード',
                href: isStore ? storeEntryPath() : '/platform/dashboard',
                icon: HouseIcon,
              },
            ],
          },
        ]);
      }
    };

    fetchMenus();
  }, []);

  return (
    <aside className="w-64 bg-card shrink-0 hidden md:block border-r">
      <div className="h-16 flex items-center px-6 border-b">
        <span className="text-xl font-bold tracking-wider text-primary-strong">
          {role === 'store' ? 'STORE' : 'PLATFORM'}
        </span>
      </div>
      <div className="p-4 overflow-y-auto h-[calc(100vh-4rem)]">
        <nav className="space-y-8">
          {navigation.map(section => (
            <div key={section.name}>
              <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-widest mb-4 px-3">
                {section.name}
              </h3>
              <ul className="space-y-1">
                {section.items.map((item: any) => {
                  const href = resolveStoreHref(item.href, storeId);
                  const isActive = pathname === href;
                  return (
                    <li key={item.name}>
                      <Link
                        href={href}
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
