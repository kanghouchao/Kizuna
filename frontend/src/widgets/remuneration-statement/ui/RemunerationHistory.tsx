'use client';
import { remunerationApi, type Bonus, type Guarantee } from '../api';
import { fromSpringPage } from '@/shared/api';
import { useListPage } from '@/shared/lib';
import {
  ChangeEntry,
  ChangeLog,
  ChangeTable,
  Dialog,
  DialogFrame,
  Pager,
  RegionError,
} from '@/shared/ui';

type Kind = 'guarantee' | 'bonus';
const yen = (value: number) => `¥${value.toLocaleString('ja-JP')}`;
const fields: Record<Kind, [string, (value: Guarantee & Bonus) => string][]> = {
  guarantee: [
    ['適用開始日', value => value.effective_from],
    ['状態', value => (value.state === 'STOPPED' ? '停止' : '保証')],
    ['日額', value => (value.daily_amount === undefined ? '—' : yen(value.daily_amount))],
    ['理由', value => value.reason],
    ['取消', value => value.cancelled_at ?? 'なし'],
  ],
  bonus: [
    ['支給日', value => value.award_date],
    ['金額', value => yen(value.amount)],
    ['有効額', value => yen(value.effective_amount)],
    ['理由', value => value.reason],
    ['取消', value => value.cancelled_at ?? 'なし'],
  ],
};
const show = (value: Bonus | Guarantee | undefined, read: (value: Guarantee & Bonus) => string) =>
  value ? read(value as Guarantee & Bonus) : '—';

export function RemunerationHistory({
  kind,
  id,
  open,
  onClose,
}: {
  kind: Kind;
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
      <DialogFrame size="md" title="変更履歴">
        {history.isLoading ? (
          <p role="status">読み込み中...</p>
        ) : history.failed ? (
          <RegionError
            message="履歴を取得できませんでした。"
            onRetry={() => void history.reload()}
          />
        ) : (
          <ChangeLog>
            {history.rows.map(change => (
              <ChangeEntry
                key={change.id}
                title={change.created_at}
                meta={
                  <>
                    <p>操作者 {change.actor_id}</p>
                    <p className="whitespace-pre-wrap">{change.reason}</p>
                  </>
                }
              >
                <ChangeTable
                  rows={fields[kind].map(([label, read]) => ({
                    label,
                    before: show(change.before_value, read),
                    after: show(change.after_value, read),
                  }))}
                />
              </ChangeEntry>
            ))}
          </ChangeLog>
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
      </DialogFrame>
    </Dialog>
  );
}
