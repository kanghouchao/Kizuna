'use client';

import { AuthorizedStore } from '@/entities/user';
import { Button, Checkbox, Label, RegionError } from '@/shared/ui';

interface StoreChecklistProps {
  /** 店舗目録。取得は呼び出し元（一覧ページ）が 1 回だけ行い、ここでは取得しない。 */
  stores: AuthorizedStore[];
  isLoading: boolean;
  /** 目録の取得に失敗した状態。選択肢が無いのが事実なのか読めなかったのかを区別する。 */
  failed: boolean;
  /** 目録の取り直し。取得失敗が続いた場合の手動回復導線（空の個別店舗指定はサーバが 400 で拒む）。 */
  onReload: () => void;
  storeIds: number[];
  onChange: (storeIds: number[]) => void;
}

/** 店舗目録から個別店舗を選ぶチェックボックス一覧。全選択・全解除と、目録の読み込み中・失敗・空の表示を持つ。 */
export function StoreChecklist({
  stores,
  isLoading,
  failed,
  onReload,
  storeIds,
  onChange,
}: StoreChecklistProps) {
  const storeIdsInCatalog = stores.flatMap(store => (store.id === undefined ? [] : [store.id]));
  const toggleStore = (id: number) =>
    onChange(
      storeIds.includes(id) ? storeIds.filter(storeId => storeId !== id) : [...storeIds, id]
    );

  return (
    <div className="@container ml-6 space-y-3 rounded-md border p-3">
      {isLoading ? (
        <p className="text-sm text-muted-foreground">読み込み中...</p>
      ) : failed ? (
        // 「選択肢がありません」と言い切ると、読めなかっただけの状態が事実に化ける
        <RegionError message="店舗一覧の取得に失敗しました" onRetry={onReload} />
      ) : storeIdsInCatalog.length === 0 ? (
        // 空のままでは選べる店舗が無く、個別店舗の空提出はサーバが拒む。
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
                onClick={() => onChange(storeIdsInCatalog)}
              >
                すべて選択
              </Button>
              <Button type="button" size="sm" variant="outline" onClick={() => onChange([])}>
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
  );
}
