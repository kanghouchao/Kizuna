'use client';

import { PlatformStore, PlatformStoreScopeType, platformAuthApi } from '@/entities/user';
import { useManagedList } from '@/shared/lib';

interface SettlementScopePickerProps {
  scopeType: PlatformStoreScopeType | null;
  storeIds: number[];
  onChange: (next: { scopeType: PlatformStoreScopeType | null; storeIds: number[] }) => void;
}

/**
 * 精算範囲の編集（なし / 全店舗 / 指定店舗の 3 択 — 次元が表現できること）。
 * 経理系能力を持たない通常スタッフは「なし」のままでよい。
 */
export function SettlementScopePicker({
  scopeType,
  storeIds,
  onChange,
}: SettlementScopePickerProps) {
  const { items: stores, isLoading } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );

  const toggleStore = (id: number) => {
    const nextIds = storeIds.includes(id)
      ? storeIds.filter(storeId => storeId !== id)
      : [...storeIds, id];
    onChange({ scopeType: 'SPECIFIC_STORES', storeIds: nextIds });
  };

  return (
    <div>
      <span className="mb-1 block text-sm font-medium text-gray-700">精算範囲（任意）</span>
      <div className="space-y-2">
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="radio"
            name="settlement-scope-type"
            checked={scopeType === null}
            onChange={() => onChange({ scopeType: null, storeIds: [] })}
            className="border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          なし
        </label>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="radio"
            name="settlement-scope-type"
            checked={scopeType === 'ALL_STORES'}
            onChange={() => onChange({ scopeType: 'ALL_STORES', storeIds: [] })}
            className="border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          全店舗
        </label>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="radio"
            name="settlement-scope-type"
            checked={scopeType === 'SPECIFIC_STORES'}
            onChange={() => onChange({ scopeType: 'SPECIFIC_STORES', storeIds })}
            className="border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          指定店舗
        </label>
        {scopeType === 'SPECIFIC_STORES' && (
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
