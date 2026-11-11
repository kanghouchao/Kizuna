import Link from 'next/link';
import { LayoutGridIcon } from 'lucide-react';

export interface RailStore {
  id: number;
  name: string;
  shortName: string;
}

interface ContextRailProps {
  /** グループのダッシュボード。グループの木を持たない利用者には無い。 */
  groupHref: string | undefined;
  stores: RailStore[];
  storeHref: (id: number) => string;
  /** 'group'、または URL が名指す店舗 id（文字列）。どちらでもない画面では無い。 */
  selected: string | undefined;
}

const BUTTON =
  'flex size-10 shrink-0 items-center justify-center rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
const SELECTED = 'bg-primary/10 text-primary-strong ring-2 ring-primary';
const IDLE = 'text-muted-foreground hover:bg-muted hover:text-foreground';

/** 左端の文脈レール。グループと授権店舗を同列の文脈として並べ、選ぶとその文脈のダッシュボードへ移る。 */
export function ContextRail({ groupHref, stores, storeHref, selected }: ContextRailProps) {
  return (
    <nav
      aria-label="文脈"
      className="flex h-screen w-14 shrink-0 flex-col items-center gap-2 overflow-y-auto border-r py-3"
    >
      {groupHref && (
        <Link
          href={groupHref}
          title="グループ"
          aria-label="グループ"
          aria-current={selected === 'group' ? 'page' : undefined}
          className={`${BUTTON} ${selected === 'group' ? SELECTED : IDLE}`}
        >
          <LayoutGridIcon className="size-5" />
        </Link>
      )}
      {groupHref && stores.length > 0 && <div className="my-1 h-px w-8 shrink-0 bg-border" />}
      {stores.map(store => (
        <Link
          key={store.id}
          href={storeHref(store.id)}
          title={store.name}
          aria-label={store.name}
          aria-current={selected === String(store.id) ? 'page' : undefined}
          className={`${BUTTON} overflow-hidden text-xs font-semibold ${
            selected === String(store.id) ? SELECTED : IDLE
          }`}
        >
          {store.shortName}
        </Link>
      ))}
    </nav>
  );
}
