'use client';

import { useId, type Ref, type ComponentProps } from 'react';
import { AuthorizedStore, StoreScopeType } from '@/entities/user';
import { Button, Checkbox, Label, RadioGroup, RadioGroupItem, RegionError } from '@/shared/ui';

interface StoreSetPickerProps extends Pick<
  ComponentProps<'div'>,
  'id' | 'aria-invalid' | 'aria-describedby'
> {
  ref?: Ref<HTMLElement>;
  /** 見出し。既定は授権の語彙で、店舗集合を別の意味で使う面（特典規則の発火範囲など）が言い換える。 */
  label?: string;
  /** 店舗目録。取得は呼び出し元（一覧ページ）が 1 回だけ行い、ここでは取得しない。 */
  stores: AuthorizedStore[];
  isLoading: boolean;
  /** 目録の取得に失敗した状態。選択肢が無いのが事実なのか読めなかったのかを区別する。 */
  failed: boolean;
  /** 目録の取り直し。取得失敗が続いた場合の手動回復導線（空の SPECIFIC_STORES はサーバが 400 で拒む）。 */
  onReload: () => void;
  storeScopeType: StoreScopeType;
  storeIds: number[];
  onChange: (next: { storeScopeType: StoreScopeType; storeIds: number[] }) => void;
}

/** 「全店舗」と「個別店舗」の択一に、個別店舗のチェックボックスを添えて店舗集合を編集する共通部品。 */
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
  const storeIdsInCatalog = stores.flatMap(store => (store.id === undefined ? [] : [store.id]));
  const toggleStore = (id: number) => {
    const nextIds = storeIds.includes(id)
      ? storeIds.filter(storeId => storeId !== id)
      : [...storeIds, id];
    onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds: nextIds });
  };

  return (
    <div role="group" aria-labelledby={labelId} {...groupProps} className="space-y-2">
      <span id={labelId} className="block text-sm font-medium text-foreground">
        {label}
      </span>
      <RadioGroup
        aria-labelledby={labelId}
        value={storeScopeType}
        onValueChange={value =>
          onChange(
            value === 'ALL_STORES'
              ? { storeScopeType: 'ALL_STORES', storeIds: [] }
              : { storeScopeType: 'SPECIFIC_STORES', storeIds }
          )
        }
        className="gap-2"
      >
        <Label className="min-w-0 font-normal break-words">
          <RadioGroupItem value="ALL_STORES" />
          全店舗
        </Label>
        <Label className="min-w-0 font-normal break-words">
          <RadioGroupItem value="SPECIFIC_STORES" ref={ref} />
          個別店舗
        </Label>
      </RadioGroup>
      {storeScopeType === 'SPECIFIC_STORES' && (
        <div className="@container ml-6 space-y-3 rounded-md border p-3">
          {isLoading ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : failed ? (
            // 「選択肢がありません」と言い切ると、読めなかっただけの状態が事実に化ける
            <RegionError message="店舗一覧の取得に失敗しました" onRetry={onReload} />
          ) : storeIdsInCatalog.length === 0 ? (
            // 空のままでは選べる店舗が無く、SPECIFIC_STORES の空提出はサーバが拒む。
            // 自動再試行が尽きても閉じ直さずに回復できる手動導線を残す
            <div className="space-y-2">
              <p className="text-sm text-muted-foreground">店舗の選択肢がありません。</p>
              <Button type="button" variant="outline" size="sm" onClick={onReload}>
                再読み込み
              </Button>
            </div>
          ) : (
            <>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-foreground tabular-nums">
                  {storeIds.length} / {storeIdsInCatalog.length} 店舗を選択中
                </p>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds: storeIdsInCatalog })
                    }
                  >
                    すべて選択
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => onChange({ storeScopeType: 'SPECIFIC_STORES', storeIds: [] })}
                  >
                    すべて解除
                  </Button>
                </div>
              </div>
              <div className="grid gap-x-6 gap-y-2 @lg:grid-cols-2">
                {stores.map(store => {
                  const id = store.id;
                  if (id === undefined) return null;
                  return (
                    <Label key={id} className="min-w-0 items-start font-normal break-words">
                      <Checkbox
                        className="mt-0.5"
                        checked={storeIds.includes(id)}
                        onCheckedChange={() => toggleStore(id)}
                      />
                      {store.name}
                    </Label>
                  );
                })}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
