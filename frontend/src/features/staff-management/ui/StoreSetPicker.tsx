'use client';

import { PlatformStore, PlatformStoreScopeType } from '@/entities/user';
import { Label } from '@/shared/ui';

interface StoreSetPickerProps {
  /** 店舗目録。取得は呼び出し元（一覧ページ）が 1 回だけ行い、ここでは取得しない。 */
  stores: PlatformStore[];
  isLoading: boolean;
  storeScopeType: PlatformStoreScopeType;
  storeIds: number[];
  onChange: (next: { storeScopeType: PlatformStoreScopeType; storeIds: number[] }) => void;
}

/** 「全店舗」ラジオ+個別店舗チェックボックスの2択で店舗集合を編集する共通部品。 */
export function StoreSetPicker({
  stores,
  isLoading,
  storeScopeType,
  storeIds,
  onChange,
}: StoreSetPickerProps) {
  const toggleStore = (id: number) => {
    const nextIds = storeIds.includes(id)
      ? storeIds.filter(storeId => storeId !== id)
      : [...storeIds, id];
    onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds: nextIds });
  };

  return (
    <div>
      <span className="mb-1 block text-sm font-medium text-foreground">担当店舗</span>
      <div className="space-y-2">
        <Label className="font-normal">
          <input
            type="radio"
            name="store-scope-type"
            checked={storeScopeType === 'ALL_STORES'}
            onChange={() => onChange({ storeScopeType: 'ALL_STORES', storeIds: [] })}
          />
          全店舗
        </Label>
        <Label className="font-normal">
          <input
            type="radio"
            name="store-scope-type"
            checked={storeScopeType === 'SPECIFIC_STORES'}
            onChange={() => onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds })}
          />
          個別店舗
        </Label>
        {storeScopeType === 'SPECIFIC_STORES' && (
          <div className="ml-6 space-y-1 rounded-md border p-3">
            {isLoading ? (
              <p className="text-sm text-muted-foreground">読み込み中...</p>
            ) : (
              stores.map(store => {
                const id = store.id;
                if (id === undefined) return null;
                return (
                  <Label key={id} className="font-normal">
                    <input
                      type="checkbox"
                      checked={storeIds.includes(id)}
                      onChange={() => toggleStore(id)}
                    />
                    {store.name}
                  </Label>
                );
              })
            )}
          </div>
        )}
      </div>
    </div>
  );
}
