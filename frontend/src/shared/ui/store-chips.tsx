'use client';

import { useEffect } from 'react';
import { Toggle } from '@base-ui/react/toggle';
import { ToggleGroup } from '@base-ui/react/toggle-group';

interface StoreChipsProps {
  stores: readonly { id: string; name: string }[];
  value: string | null;
  onValueChange: (storeId: string) => void;
}

/**
 * 本人ポータルの店舗の切替。複数店舗に在籍するときだけ横スクロールのチップを出す。
 * 1 店舗ならチップを出さずにその店舗を選ぶ — 各画面が同じ分岐を持たないよう、ここで一度だけ決める。
 */
export function StoreChips({ stores, value, onValueChange }: StoreChipsProps) {
  const only = stores.length === 1 ? stores[0].id : null;
  useEffect(() => {
    if (only !== null && value !== only) onValueChange(only);
  }, [only, value, onValueChange]);

  if (stores.length < 2) return null;
  return (
    <ToggleGroup
      aria-label="店舗"
      value={value === null ? [] : [value]}
      onValueChange={next => {
        if (next.length > 0) onValueChange(next[0]);
      }}
      className="flex gap-2 overflow-x-auto pb-1"
    >
      {stores.map(store => (
        <Toggle
          key={store.id}
          value={store.id}
          title={store.name}
          className="flex min-h-11 max-w-60 shrink-0 items-center rounded-full border bg-card px-4 text-sm font-medium text-foreground hover:not-data-pressed:bg-muted focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none data-pressed:border-primary data-pressed:bg-primary/10 data-pressed:text-primary-strong"
        >
          <span className="truncate">{store.name}</span>
        </Toggle>
      ))}
    </ToggleGroup>
  );
}
