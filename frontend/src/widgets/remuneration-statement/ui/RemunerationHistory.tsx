'use client';
import { useState } from 'react';
import { remunerationApi, type Bonus, type Guarantee } from '../api';
import { useResource } from '@/shared/lib';
import { Button, Dialog, DialogContent, DialogHeader, DialogTitle, RegionError } from '@/shared/ui';
const summary = (value: Bonus | Guarantee | undefined) => {
  if (!value) return '記録なし';
  if ('effective_from' in value)
    return `${value.effective_from}〜 ${value.state === 'STOPPED' ? '停止' : `日額 ¥${value.daily_amount?.toLocaleString('ja-JP')}`} ${value.cancelled_at ? '取消済み' : ''}`;
  return `${value.award_date} ¥${value.effective_amount.toLocaleString('ja-JP')} ${value.cancelled_at ? '取消済み' : ''}`;
};
export function RemunerationHistory({
  kind,
  id,
  open,
  onClose,
}: {
  kind: 'guarantee' | 'bonus';
  id: string;
  open: boolean;
  onClose: () => void;
}) {
  const [cursor, setCursor] = useState<string>();
  const history = useResource(() => remunerationApi.changes(kind, id, cursor), [kind, id, cursor]);
  return (
    <Dialog
      open={open}
      onOpenChange={open => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>変更履歴</DialogTitle>
        </DialogHeader>
        {history.isLoading ? (
          <p role="status">読み込み中...</p>
        ) : history.failure !== null ? (
          <RegionError message="履歴を取得できませんでした。" onRetry={history.reload} />
        ) : (
          history.data?.content.map(change => (
            <article className="space-y-2 rounded-lg border p-4" key={change.id}>
              <p>
                {change.created_at} / 操作者 {change.actor_id}
              </p>
              <p>{change.reason}</p>
              <p>変更前：{summary(change.before_value)}</p>
              <p>変更後：{summary(change.after_value)}</p>
            </article>
          ))
        )}
        {history.failure === null && !history.isLoading && (
          <div className="flex gap-3">
            {cursor && (
              <Button variant="outline" onClick={() => setCursor(undefined)}>
                最初へ
              </Button>
            )}
            {history.data?.next_cursor && (
              <Button variant="outline" onClick={() => setCursor(history.data?.next_cursor)}>
                次の履歴
              </Button>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
