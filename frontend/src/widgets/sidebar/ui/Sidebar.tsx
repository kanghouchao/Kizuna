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
import { useStoreContext } from '@/entities/user';
import { isStorePath } from '@/shared/lib';
import { ContextRail, type RailStore } from './ContextRail';

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
      <span className="min-w-0 truncate">{item.name}</span>
    </Link>
  );
}

// 見出しの順と見出し内の項目の順はメニュー（seed）のまま、項目のパスで文脈に振り分ける。
// 項目が 1 つも残らない見出しは出さない。
function sectionsFor(navigation: NavigationSection[], storeLevel: boolean): NavigationSection[] {
  return navigation
    .filter(section => !section.href)
    .map(section => ({
      ...section,
      items: section.items.filter(item => isStorePath(item.href) === storeLevel),
    }))
    .filter(section => section.items.length > 0);
}

export function Sidebar() {
  const pathname = usePathname();
  const { stores, currentStoreId, loadFailed, switchStore } = useStoreContext();
  const [navigation, setNavigation] = useState<NavigationSection[]>([]);
  // 利用者が明示的に選んだ文脈。未選択の間は既定（店舗級の項目を見られるなら現在店舗）に従う。
  const [picked, setPicked] = useState<'group' | 'store'>();

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

  // 授権店舗が解決する前に描くと、店舗の文脈が既定の利用者に一瞬グループのメニューを見せてしまう。
  // 取得失敗は解決済みとして扱い、グループ級だけで描く。
  const ready = navigation.length > 0 && (stores !== null || loadFailed);

  const groupSections = sectionsFor(navigation, false);
  const storeSections = sectionsFor(navigation, true);
  const railStores: RailStore[] = (stores ?? []).flatMap(store =>
    store.id === undefined
      ? []
      : [{ id: store.id, name: store.name ?? '', shortName: store.short_name ?? '' }]
  );

  const hasGroup = groupSections.length > 0;
  const hasStore = storeSections.length > 0 && railStores.length > 0;
  const showRail = (hasGroup ? 1 : 0) + (hasStore ? railStores.length : 0) >= 2;
  const showing: 'group' | 'store' = !hasStore
    ? 'group'
    : !hasGroup
      ? 'store'
      : (picked ?? 'store');

  const currentName = railStores.find(store => String(store.id) === currentStoreId)?.name;
  const heading = showing === 'group' ? 'グループ' : (currentName ?? '');
  const dashboards = ready ? navigation.filter(section => section.href) : [];

  return (
    <aside className="flex shrink-0 bg-card">
      {ready && showRail && (
        <ContextRail
          showGroup={hasGroup}
          stores={hasStore ? railStores : []}
          selected={showing === 'group' ? 'group' : currentStoreId}
          onSelectGroup={() => setPicked('group')}
          onSelectStore={id => {
            switchStore(id);
            setPicked('store');
          }}
        />
      )}

      <div className={`${showRail ? 'w-[200px]' : 'w-64'} min-w-0 border-r`}>
        <div className="h-16 flex flex-col justify-center px-4 border-b">
          <span className="text-xs font-bold tracking-wider text-primary-strong">Kizuna</span>
          <span className="truncate font-semibold text-foreground" title={heading}>
            {heading}
          </span>
        </div>
        <div className="p-3 overflow-y-auto h-[calc(100vh-4rem)]">
          <nav className="space-y-8">
            {dashboards.map(section => (
              <NavLink
                key={section.name}
                item={{ name: section.name, href: section.href!, icon: section.icon }}
                isActive={pathname === section.href}
              />
            ))}
            {(!ready ? [] : showing === 'group' ? groupSections : storeSections).map(section => (
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
            ))}
          </nav>
        </div>
      </div>
    </aside>
  );
}
