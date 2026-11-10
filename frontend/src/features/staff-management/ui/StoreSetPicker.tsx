'use client';

import { useId } from 'react';
import { AuthorizedStore } from '@/entities/user';
import { StoreChecklist } from './StoreChecklist';

interface StoreSetPickerProps {
  label?: string;
  stores: AuthorizedStore[];
  isLoading: boolean;
  failed: boolean;
  onReload: () => void;
  storeIds: number[];
  onChange: (storeIds: number[]) => void;
}

/** 授権の店舗集合を店舗の個別選択で編集する。全選択はその時点の店舗を選ぶだけで、空の集合も選べる。 */
export function StoreSetPicker({
  label = '担当店舗',
  stores,
  isLoading,
  failed,
  onReload,
  storeIds,
  onChange,
}: StoreSetPickerProps) {
  const labelId = useId();

  return (
    <div role="group" aria-labelledby={labelId} className="space-y-2">
      <span id={labelId} className="block text-sm font-medium text-foreground">
        {label}
      </span>
      <StoreChecklist
        stores={stores}
        isLoading={isLoading}
        failed={failed}
        onReload={onReload}
        storeIds={storeIds}
        onChange={onChange}
      />
    </div>
  );
}
