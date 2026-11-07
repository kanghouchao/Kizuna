'use client';

import { Toggle } from '@base-ui/react/toggle';
import { ToggleGroup } from '@base-ui/react/toggle-group';
import { cn } from '@/shared/lib/utils';

export interface SegmentedOption<V extends string> {
  value: V;
  label: string;
}

interface SegmentedProps<V extends string> {
  /** 切替の accessible name（例: 「絞り込み」）。 */
  label: string;
  options: readonly SegmentedOption<V>[];
  value: V;
  onValueChange: (value: V) => void;
  className?: string;
}

/**
 * 画面の中の絞り込み・表示の切替（segmented control）。常にどれか一つが選ばれている。
 * 画面の切り替え（URL が変わる）は PortalShell の区分で行い、これを使わない。
 */
export function Segmented<V extends string>({
  label,
  options,
  value,
  onValueChange,
  className,
}: SegmentedProps<V>) {
  return (
    <ToggleGroup
      aria-label={label}
      value={[value]}
      // 押下中の区分をもう一度押すと Base UI は空配列を渡す。選択無しの状態を作らない。
      onValueChange={next => {
        if (next.length > 0) onValueChange(next[0]);
      }}
      className={cn('flex rounded-lg bg-muted p-1', className)}
    >
      {options.map(option => (
        <Toggle
          key={option.value}
          value={option.value}
          className="flex min-h-11 flex-1 items-center justify-center rounded-md px-3 text-sm font-medium text-foreground hover:not-data-pressed:bg-card/60 focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none data-pressed:bg-card data-pressed:shadow-sm"
        >
          {option.label}
        </Toggle>
      ))}
    </ToggleGroup>
  );
}
