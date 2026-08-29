'use client';

import { PlatformStore, PlatformStoreScopeType } from '@/entities/user';
import { Button, Label, RegionError } from '@/shared/ui';

interface StoreSetPickerProps {
  /** 見出し。既定は授権の語彙で、店舗集合を別の意味で使う面（特典規則の発火範囲など）が言い換える。 */
  label?: string;
  /** 店舗目録。取得は呼び出し元（一覧ページ）が 1 回だけ行い、ここでは取得しない。 */
  stores: PlatformStore[];
  isLoading: boolean;
  /** 目録の取得に失敗した状態。選択肢が無いのが事実なのか読めなかったのかを区別する。 */
  failed: boolean;
  /** 目録の取り直し。取得失敗が続いた場合の手動回復導線（空の SPECIFIC_STORES はサーバが 400 で拒む）。 */
  onReload: () => void;
  storeScopeType: PlatformStoreScopeType;
  storeIds: number[];
  onChange: (next: { storeScopeType: PlatformStoreScopeType; storeIds: number[] }) => void;
}

/** 「全店舗」ラジオ+個別店舗チェックボックスの2択で店舗集合を編集する共通部品。 */
export function StoreSetPicker({
  label = '担当店舗',
  stores,
  isLoading,
  failed,
  onReload,
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
      <span className="mb-1 block text-sm font-medium text-foreground">{label}</span>
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
            ) : failed ? (
              // 「選択肢がありません」と言い切ると、読めなかっただけの状態が事実に化ける
              <RegionError message="店舗一覧の取得に失敗しました" onRetry={onReload} />
            ) : stores.length === 0 ? (
              // 空のままでは選べる店舗が無く、SPECIFIC_STORES の空提出はサーバが拒む。
              // 自動再試行が尽きても閉じ直さずに回復できる手動導線を残す
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">店舗の選択肢がありません。</p>
                <Button type="button" variant="outline" size="sm" onClick={onReload}>
                  再読み込み
                </Button>
              </div>
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
