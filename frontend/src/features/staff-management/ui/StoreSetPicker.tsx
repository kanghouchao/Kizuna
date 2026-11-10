'use client';

import { useId, type Ref, type ComponentProps } from 'react';
import { AuthorizedStore, StoreScopeType } from '@/entities/user';
import { StoreChecklist } from './StoreChecklist';

interface StoreSetPickerProps extends Pick<
  ComponentProps<'div'>,
  'id' | 'aria-invalid' | 'aria-describedby'
> {
  ref?: Ref<HTMLDivElement>;
  label?: string;
  stores: AuthorizedStore[];
  isLoading: boolean;
  failed: boolean;
  onReload: () => void;
  storeScopeType: StoreScopeType;
  storeIds: number[];
  onChange: (next: { storeScopeType: StoreScopeType; storeIds: number[] }) => void;
}

/**
 * 授権の店舗集合を店舗の個別選択で編集する。全選択もその時点の店舗を選ぶだけで、全店舗は授与しない。
 * 既に全店舗を持つ授権は、店舗を選び直すまでそのまま保つ（保存だけで担当が狭まらないように）。
 */
export function StoreSetPicker({
  label = '担当店舗',
  stores,
  isLoading,
  failed,
  onReload,
  storeScopeType,
  storeIds,
  onChange,
  ref,
  ...groupProps
}: StoreSetPickerProps) {
  const labelId = useId();

  return (
    <div
      role="group"
      aria-labelledby={labelId}
      tabIndex={-1}
      ref={ref}
      {...groupProps}
      className="space-y-2 outline-none"
    >
      <span id={labelId} className="block text-sm font-medium text-foreground">
        {label}
      </span>
      {storeScopeType === 'ALL_STORES' && (
        <p className="text-sm text-muted-foreground">
          現在は全店舗（今後追加される店舗も含む）です。店舗を選ぶと、選んだ店舗だけに変わります。
        </p>
      )}
      <StoreChecklist
        stores={stores}
        isLoading={isLoading}
        failed={failed}
        onReload={onReload}
        storeIds={storeIds}
        onChange={nextIds => onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds: nextIds })}
      />
    </div>
  );
}
