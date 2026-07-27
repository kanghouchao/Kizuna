'use client';

import { PlatformStore, PlatformStoreScopeType, platformAuthApi } from '@/entities/user';
import { useManagedList } from '@/shared/lib';
import { Label } from '@/shared/ui';

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
      <span className="mb-1 block text-sm font-medium text-foreground">精算範囲（任意）</span>
      <div className="space-y-2">
        <Label className="font-normal">
          <input
            type="radio"
            name="settlement-scope-type"
            checked={scopeType === null}
            onChange={() => onChange({ scopeType: null, storeIds: [] })}
          />
          なし
        </Label>
        <Label className="font-normal">
          <input
            type="radio"
            name="settlement-scope-type"
            checked={scopeType === 'ALL_STORES'}
            onChange={() => onChange({ scopeType: 'ALL_STORES', storeIds: [] })}
          />
          全店舗
        </Label>
        <Label className="font-normal">
          <input
            type="radio"
            name="settlement-scope-type"
            checked={scopeType === 'SPECIFIC_STORES'}
            onChange={() => onChange({ scopeType: 'SPECIFIC_STORES', storeIds })}
          />
          指定店舗
        </Label>
        {scopeType === 'SPECIFIC_STORES' && (
          <div className="ml-6 space-y-1 rounded-md border p-3">
            {isLoading ? (
              <p className="text-sm text-muted-foreground">読み込み中...</p>
            ) : (
              stores.map(store => (
                <Label key={store.id} className="font-normal">
                  <input
                    type="checkbox"
                    checked={storeIds.includes(store.id)}
                    onChange={() => toggleStore(store.id)}
                  />
                  {store.name}
                </Label>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
