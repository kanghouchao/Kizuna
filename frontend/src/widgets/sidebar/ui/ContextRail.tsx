import { LayoutGridIcon } from 'lucide-react';

export interface RailStore {
  id: number;
  name: string;
  shortName: string;
}

interface ContextRailProps {
  showGroup: boolean;
  stores: RailStore[];
  /** 'group'、または選択中の店舗 id（文字列）。 */
  selected: string | undefined;
  onSelectGroup: () => void;
  onSelectStore: (id: number) => void;
}

const BUTTON =
  'flex size-10 shrink-0 items-center justify-center rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
const SELECTED = 'bg-primary/10 text-primary-strong ring-2 ring-primary';
const IDLE = 'text-muted-foreground hover:bg-muted hover:text-foreground';

/** 左端の文脈レール。グループと授権店舗を同列の文脈として並べる。 */
export function ContextRail({
  showGroup,
  stores,
  selected,
  onSelectGroup,
  onSelectStore,
}: ContextRailProps) {
  return (
    <div className="flex h-screen w-14 shrink-0 flex-col items-center gap-2 overflow-y-auto border-r py-3">
      {showGroup && (
        <button
          type="button"
          title="グループ"
          aria-label="グループ"
          aria-pressed={selected === 'group'}
          onClick={onSelectGroup}
          className={`${BUTTON} ${selected === 'group' ? SELECTED : IDLE}`}
        >
          <LayoutGridIcon className="size-5" />
        </button>
      )}
      {showGroup && stores.length > 0 && <div className="my-1 h-px w-8 shrink-0 bg-border" />}
      {stores.map(store => (
        <button
          key={store.id}
          type="button"
          title={store.name}
          aria-label={store.name}
          aria-pressed={selected === String(store.id)}
          onClick={() => onSelectStore(store.id)}
          className={`${BUTTON} overflow-hidden text-xs font-semibold ${
            selected === String(store.id) ? SELECTED : IDLE
          }`}
        >
          {store.shortName}
        </button>
      ))}
    </div>
  );
}
