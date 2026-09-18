'use client';

import { useState } from 'react';
import { CustomerMergeComparisonResponse, customerApi } from '@/entities/customer';
import { useResource } from '@/shared/lib';
import { Button, RegionError, TableCard } from '@/shared/ui';
import { CustomerMergeComparison } from './CustomerMergeComparison';

interface CustomerMergePanelProps {
  /** 見比べる 2 行。呼出側は 2 件そろっている間だけこの区画を描く。 */
  customerIds: [string, string];
  onMerge: (
    surviving: CustomerMergeComparisonResponse,
    merged: CustomerMergeComparisonResponse
  ) => void;
  /** 選択を解除する。行き止まりになった見比べからの出口でもある。 */
  onClear: () => void;
  /**
   * 統合の実行中。選択を握るのは呼出側なので、実行中に選択を変えさせないための状態も
   * 呼出側が持つ。
   */
  isSubmitting: boolean;
}

export function CustomerMergePanel({
  customerIds,
  onMerge,
  onClear,
  isSubmitting,
}: CustomerMergePanelProps) {
  const { data, isLoading, failure, reload } = useResource(
    () => customerApi.mergeComparison(customerIds[0], customerIds[1]),
    customerIds
  );
  const [survivingId, setSurvivingId] = useState<string | null>(null);

  // 端点は 2 行そろわなければ 404 を返す。組にできない応答で見比べを描かないための型の絞り込み
  const rows: [CustomerMergeComparisonResponse, CustomerMergeComparisonResponse] | null =
    data !== null && data.length >= 2 ? [data[0], data[1]] : null;
  const surviving = rows?.find(row => row.id === survivingId);
  const merged = rows?.find(row => row.id !== survivingId);

  return (
    <TableCard>
      {isLoading ? (
        <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
      ) : failure === 'notFound' ? (
        // 選んだ行が生きていない（他者の統合が先に確定した・消された）。RegionError を使わないのは、
        // その 404 の出口が一覧へのリンクだから — この区画は一覧の上にあり、辿ってもいま居る URL に
        // 戻るだけで出口にならない。借りるのは容器の role="alert" だけで、回復手段は選び直しにする
        // （DESIGN.md がモーダルについて述べているのと同じ事情）
        <div role="alert" className="flex items-center justify-center gap-3 p-8">
          <p className="text-sm text-destructive-strong">
            選んだ顧客が見つかりません。すでに統合されている可能性があります。
          </p>
          <Button type="button" variant="outline" size="sm" onClick={onClear}>
            選択を解除
          </Button>
        </div>
      ) : failure !== null ? (
        <RegionError
          message="見比べる顧客の取得に失敗しました"
          onRetry={() => void reload()}
          className="justify-center p-8"
        />
      ) : (
        rows && (
          <CustomerMergeComparison
            rows={rows}
            survivingId={survivingId}
            onSurvivingChange={setSurvivingId}
            onMerge={() => {
              if (surviving && merged) onMerge(surviving, merged);
            }}
            disabled={isSubmitting}
          />
        )
      )}
    </TableCard>
  );
}
