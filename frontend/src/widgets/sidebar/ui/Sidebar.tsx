'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';
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
  NewspaperIcon,
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
import { type MenuVO, useMenuTrees } from '@/entities/menu';
import { useStoreContext } from '@/entities/user';
import { isStorePath, storeIdOf, storePath } from '@/shared/lib';
import { RegionError } from '@/shared/ui';
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
  NewspaperIcon,
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

const GROUP_DASHBOARD = '/admin/dashboard';
const STORE_DASHBOARD = '/dashboard';

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

// 店舗の木の path は店舗の中のパスなので、表示中の店舗の id を前に付ける。
function toSections(roots: MenuVO[], href: (path: string) => string): NavigationSection[] {
  return roots.map(section => ({
    name: section.name,
    href: section.path && href(section.path),
    icon: resolveIcon(section.icon),
    items: (section.items || []).map(item => ({
      name: item.name,
      href: item.path ? href(item.path) : '#',
      icon: resolveIcon(item.icon),
    })),
  }));
}

// どの文脈も出せない利用者（権限が無い・取得に失敗した）にも全員の着地先への導線だけは残す。
const FALLBACK: NavigationSection[] = [
  { name: 'ダッシュボード', href: GROUP_DASHBOARD, icon: HouseIcon, items: [] },
];

export function Sidebar() {
  const pathname = usePathname();
  const trees = useMenuTrees();
  const { stores, loadFailed, reload } = useStoreContext();

  // 授権店舗が解決する前に描くと、店舗の文脈が既定の利用者に一瞬グループのメニューを見せてしまう。
  // 取得失敗は解決済みとして扱い、グループの木だけで描く。一度解決したら戻さない —
  // 失敗後の再試行で reload が loadFailed を下ろしても、描いた項目を応答まで消さないため。
  const [storesSettled, setStoresSettled] = useState(false);
  if (!storesSettled && (stores !== null || loadFailed)) setStoresSettled(true);
  const ready = trees !== null && storesSettled;
  const railStores: RailStore[] = (stores ?? []).flatMap(store =>
    store.id === undefined
      ? []
      : [{ id: store.id, name: store.name ?? '', shortName: store.short_name ?? '' }]
  );

  // グループはグループの木を、各店舗のボタンは店舗の木を持つ者にだけ出す。
  const groupTree = trees?.find(tree => tree.tree === 'GROUP');
  const storeTree = trees?.find(tree => tree.tree === 'STORE');
  const hasGroup = groupTree !== undefined;
  const hasStore = storeTree !== undefined && railStores.length > 0;
  const showRail = (hasGroup ? 1 : 0) + (hasStore ? railStores.length : 0) >= 2;

  // 文脈は URL が決める。店舗の画面ならその店舗、それ以外はグループ（グループの木を持つ者に限る）。
  const urlStoreId = storeIdOf(pathname);
  const shownStore = hasStore
    ? railStores.find(store => String(store.id) === urlStoreId)
    : undefined;
  const showing: 'group' | 'store' | undefined = shownStore
    ? 'store'
    : hasGroup && !isStorePath(pathname)
      ? 'group'
      : undefined;

  const heading = showing === 'group' ? 'グループ' : (shownStore?.name ?? '');
  const roots = !ready
    ? []
    : showing === 'group'
      ? toSections(groupTree!.items, path => path)
      : showing === 'store'
        ? toSections(storeTree!.items, path => storePath(shownStore!.id, path))
        : FALLBACK;
  const dashboards = roots.filter(section => section.href);
  // 項目が 1 つも無い見出しは出さない。
  const sections = roots.filter(section => !section.href && section.items.length > 0);

  return (
    <aside className="flex shrink-0 bg-card">
      {ready && showRail && (
        <ContextRail
          groupHref={hasGroup ? GROUP_DASHBOARD : undefined}
          stores={hasStore ? railStores : []}
          storeHref={id => storePath(id, STORE_DASHBOARD)}
          selected={showing === 'group' ? 'group' : shownStore && String(shownStore.id)}
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
          {/* レールはこの領域の選択肢。取り直しの失敗では直前の一覧が残って古さが見えないため、
              一覧の有無に関わらず失敗をここで名乗る */}
          {loadFailed && (
            <RegionError
              message="店舗一覧の取得に失敗しました"
              onRetry={reload}
              className="mb-4 flex-wrap px-3"
            />
          )}
          <nav className="space-y-8">
            {dashboards.map(section => (
              <NavLink
                key={section.name}
                item={{ name: section.name, href: section.href!, icon: section.icon }}
                isActive={pathname === section.href}
              />
            ))}
            {sections.map(section => (
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
