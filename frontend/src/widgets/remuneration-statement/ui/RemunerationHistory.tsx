'use client';
import { remunerationApi, type Bonus, type Guarantee } from '../api';
import { fromSpringPage } from '@/shared/api';
import { useListPage } from '@/shared/lib';
import { Dialog, DialogContent, DialogHeader, DialogTitle, Pager, RegionError } from '@/shared/ui';
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
  const history = useListPage(async page =>
    fromSpringPage(await remunerationApi.changes(kind, id, page))
  );
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
        ) : history.failed ? (
          <RegionError
            message="履歴を取得できませんでした。"
            onRetry={() => void history.reload()}
          />
        ) : (
          history.rows.map(change => (
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
        {!history.failed && (
          <Pager
            page={history.page}
            pageCount={history.pageCount}
            total={history.total}
            rowCount={history.rows.length}
            disabled={history.isLoading}
            onPageChange={page => void history.onPageChange(page)}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
