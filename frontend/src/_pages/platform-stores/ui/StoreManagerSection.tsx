'use client';

import { useState } from 'react';
import { AlertTriangleIcon, PlusIcon } from 'lucide-react';
import { StoreManagerResponse, storeManagerApi } from '@/entities/user';
import { StoreManagerAppointModal } from '@/features/staff-management';
import { useDeleteAction, useManagedList } from '@/shared/lib';
import { Badge, Button, Card, CardContent, ConfirmDialog, RegionError } from '@/shared/ui';

/** 節の見出し。region の名前として参照するので id を固定で持つ。 */
const HEADING_ID = 'store-manager-section-heading';

interface StoreManagerSectionProps {
  storeId: string;
}

/**
 * 店長設定の節。店長は「STORE_MANAGER 保持かつこの店舗を担当」の導出で、任命・解任はその授権の
 * 書き換えとして起きる（ADR 0020）。
 *
 * 節そのものが ROLE_MANAGE 門なので、描くかどうかは呼び出し側が権限で決める。
 */
export function StoreManagerSection({ storeId }: StoreManagerSectionProps) {
  const [appointOpen, setAppointOpen] = useState(false);
  const {
    items: managers,
    isLoading,
    failed,
    refetch,
  } = useManagedList<StoreManagerResponse>(() => storeManagerApi.list(storeId));

  const dismissal = useDeleteAction<StoreManagerResponse>({
    remove: manager => storeManagerApi.dismiss(storeId, manager.id ?? 0),
    successMessage: '店長を解任しました',
    // 解任できない理由（最後の担当店舗・全店舗担当）は誘導先込みでサーバだけが持つ
    errorMessage: '店長の解任に失敗しました',
    onDeleted: refetch,
  });

  // 停止中の店長は着任していても操作できない。未設の注意喚起は有効な行だけで判定する。
  const hasActiveManager = managers.some(manager => manager.enabled);

  return (
    <Card>
      {/* 頁本体（店舗の基本情報フォーム）と並ぶ独立した領域なので、見出しに紐づく region として名乗る */}
      <CardContent className="space-y-4" role="region" aria-labelledby={HEADING_ID}>
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id={HEADING_ID} className="text-sm font-medium text-foreground">
              店長設定
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              この店舗の店長を任命・解任します。店長は担当店舗すべての店長になります。
            </p>
          </div>
          <Button type="button" size="sm" onClick={() => setAppointOpen(true)}>
            <PlusIcon />
            店長を任命
          </Button>
        </div>

        {failed ? (
          <RegionError message="店長の取得に失敗しました" onRetry={() => void refetch()} />
        ) : isLoading ? (
          <p className="py-4 text-center text-sm text-muted-foreground">読み込み中...</p>
        ) : (
          <>
            {!hasActiveManager && (
              <p className="flex items-center gap-2 rounded-lg bg-warning/10 px-4 py-3 text-sm text-warning-strong">
                <AlertTriangleIcon className="h-4 w-4 shrink-0" />
                この店舗には店長が設定されていません
              </p>
            )}
            {managers.length > 0 && (
              <ul className="divide-y rounded-lg border">
                {managers.map(manager => (
                  <li
                    key={manager.id}
                    className="flex items-center justify-between gap-3 px-4 py-3"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-foreground">
                        {manager.display_name}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">{manager.email}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      {!manager.enabled && (
                        <Badge
                          variant="outline"
                          className="border-transparent bg-warning/10 text-warning-strong"
                        >
                          停止中
                        </Badge>
                      )}
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="text-destructive-strong"
                        onClick={() => dismissal.ask(manager)}
                      >
                        解任
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </CardContent>

      {appointOpen && (
        <StoreManagerAppointModal
          storeId={storeId}
          onClose={() => setAppointOpen(false)}
          onAppointed={refetch}
        />
      )}

      <ConfirmDialog
        open={dismissal.target !== null}
        title="店長を解任しますか？"
        description={`${dismissal.target?.display_name ?? ''} をこの店舗の店長から外し、担当店舗からも除きます。`}
        confirmLabel="解任する"
        onConfirm={() => void dismissal.confirm()}
        onClose={dismissal.cancel}
      />
    </Card>
  );
}
