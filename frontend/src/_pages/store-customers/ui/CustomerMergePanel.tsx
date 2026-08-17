'use client';

import { useState } from 'react';
import { CustomerMergeComparisonResponse, customerApi } from '@/entities/customer';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Button, RegionError, TableCard } from '@/shared/ui';
import { CustomerMergeComparison } from './CustomerMergeComparison';
import { CustomerMergeConfirmDialog } from './CustomerMergeConfirmDialog';

interface CustomerMergePanelProps {
  /** 見比べる 2 行。呼出側は 2 件そろっている間だけこの区画を描く。 */
  customerIds: [string, string];
  /** 統合が成った後の後始末（選択の解除と一覧の取り直し）。 */
  onMerged: () => void;
  /** 選択を解除する。行き止まりになった見比べからの出口でもある。 */
  onClear: () => void;
}

/**
 * 顧客一覧で選んだ 2 行を見比べて統合する区画。
 *
 * 材料は一覧の行ではなく専用の読み口から引く。一覧の型は「絞り込んで選ぶ」ための項目しか持たず、
 * 住所も受注件数も無いので別人を見分けられない。引き直す形にすることで、選択がページ送りや
 * 検索を跨いでも生き残る（統合したい 2 行が同じページに並ぶとは限らない）。
 *
 * 2 件そろっている間だけ mount される。選択を変えるには必ず 1 件の状態を通るので、前の組の
 * 値を抱えたまま次の組を描くことはない。
 */
export function CustomerMergePanel({ customerIds, onMerged, onClear }: CustomerMergePanelProps) {
  const { data, isLoading, failure, reload } = useResource(
    () => customerApi.mergeComparison(customerIds[0], customerIds[1]),
    customerIds
  );
  const [survivingId, setSurvivingId] = useState<string | null>(null);
  const [isConfirming, setIsConfirming] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 応答は必ず 2 行。足りない応答は見比べにならないので、失敗と同じ扱いにする
  const rows: [CustomerMergeComparisonResponse, CustomerMergeComparisonResponse] | null =
    data !== null && data.length >= 2 ? [data[0], data[1]] : null;
  const surviving = rows?.find(row => row.id === survivingId);
  const merged = rows?.find(row => row.id !== survivingId);

  const handleMerge = async () => {
    if (!surviving?.id || !merged?.id) return;
    try {
      setIsSubmitting(true);
      await customerApi.merge(surviving.id, merged.id);
      notify.success('顧客を統合しました');
      setIsConfirming(false);
      onMerged();
    } catch (error) {
      // 両行が会員に認領されている 409 は「先に関連を解除する」と読める文言をサーバが返す。
      // 汎用文言に潰すと、次の一手が画面から判らなくなる
      notify.error(getApiErrorMessage(error, '顧客の統合に失敗しました'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <TableCard>
      {isLoading ? (
        <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
      ) : failure === 'notFound' ? (
        // 選んだ行が生きていない（他者の統合が先に確定した・消された）。何度押しても取れないので
        // 再試行は出さず、この画面での回復手段である選び直しを出口にする
        <div role="alert" className="flex items-center justify-center gap-3 p-8">
          <p className="text-sm text-destructive-strong">
            選んだ顧客が見つかりません。すでに統合されている可能性があります。
          </p>
          <Button type="button" variant="outline" size="sm" onClick={onClear}>
            選択を解除
          </Button>
        </div>
      ) : failure !== null || rows === null ? (
        <RegionError
          message="見比べる顧客の取得に失敗しました"
          onRetry={() => void reload()}
          className="justify-center p-8"
        />
      ) : (
        <CustomerMergeComparison
          rows={rows}
          survivingId={survivingId}
          onSurvivingChange={setSurvivingId}
          onMerge={() => setIsConfirming(true)}
          disabled={isSubmitting}
        />
      )}

      <CustomerMergeConfirmDialog
        open={isConfirming && surviving !== undefined && merged !== undefined}
        survivingName={surviving?.name ?? ''}
        mergedName={merged?.name ?? ''}
        movedOrderCount={merged?.order_count ?? 0}
        isSubmitting={isSubmitting}
        onConfirm={() => void handleMerge()}
        onClose={() => setIsConfirming(false)}
      />
    </TableCard>
  );
}
