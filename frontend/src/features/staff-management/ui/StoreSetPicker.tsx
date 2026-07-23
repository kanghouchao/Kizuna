'use client';

import { PlatformStore, PlatformStoreScopeType, platformAuthApi } from '@/entities/user';
import { useManagedList } from '@/shared/lib';

interface StoreSetPickerProps {
  storeScopeType: PlatformStoreScopeType;
  storeIds: number[];
  onChange: (next: { storeScopeType: PlatformStoreScopeType; storeIds: number[] }) => void;
}

/** 「全店舗」ラジオ+個別店舗チェックボックスの2択で店舗集合を編集する共通部品。 */
export function StoreSetPicker({ storeScopeType, storeIds, onChange }: StoreSetPickerProps) {
  const { items: stores, isLoading } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );

  const toggleStore = (id: number) => {
    const nextIds = storeIds.includes(id)
      ? storeIds.filter(storeId => storeId !== id)
      : [...storeIds, id];
    onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds: nextIds });
  };

  return (
    <div>
      <span className="mb-1 block text-sm font-medium text-gray-700">担当店舗</span>
      <div className="space-y-2">
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="radio"
            name="store-scope-type"
            checked={storeScopeType === 'ALL_STORES'}
            onChange={() => onChange({ storeScopeType: 'ALL_STORES', storeIds: [] })}
            className="border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          全店舗
        </label>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="radio"
            name="store-scope-type"
            checked={storeScopeType === 'SPECIFIC_STORES'}
            onChange={() => onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds })}
            className="border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          個別店舗
        </label>
        {storeScopeType === 'SPECIFIC_STORES' && (
          <div className="ml-6 space-y-1 rounded-md border border-gray-200 p-3">
            {isLoading ? (
              <p className="text-sm text-gray-500">読み込み中...</p>
            ) : (
              stores.map(store => (
                <label key={store.id} className="flex items-center gap-2 text-sm text-gray-700">
                  <input
                    type="checkbox"
                    checked={storeIds.includes(store.id)}
                    onChange={() => toggleStore(store.id)}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  {store.name}
                </label>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
