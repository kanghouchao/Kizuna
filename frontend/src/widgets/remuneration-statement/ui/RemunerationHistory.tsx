'use client';
import { remunerationApi, type Bonus, type Guarantee } from '../api';
import { fromSpringPage } from '@/shared/api';
import { useListPage } from '@/shared/lib';
import { yen } from './yen';
import {
  ChangeEntry,
  ChangeLog,
  ChangeTable,
  Dialog,
  DialogFrame,
  Pager,
  RegionError,
} from '@/shared/ui';

type Field<T> = [string, (value: T) => string];
const guaranteeFields: Field<Guarantee>[] = [
  ['適用開始日', value => value.effective_from],
  ['状態', value => (value.state === 'STOPPED' ? '停止' : '保証')],
  ['日額', value => (value.state === 'STOPPED' ? '—' : yen(value.daily_amount))],
  ['理由', value => value.reason],
  ['取消', value => value.cancelled_at ?? 'なし'],
];
const bonusFields: Field<Bonus>[] = [
  ['支給日', value => value.award_date],
  ['金額', value => yen(value.amount)],
  ['有効額', value => yen(value.effective_amount)],
  ['理由', value => value.reason],
  ['取消', value => value.cancelled_at ?? 'なし'],
];
function changeRows<T>(fields: Field<T>[], before: T | undefined, after: T | undefined) {
  return fields.map(([label, read]) => ({
    label,
    before: before ? read(before) : '—',
    after: after ? read(after) : '—',
  }));
}

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
                title={`${change.created_at} · 操作者 ${change.actor_id}`}
                meta={<p className="whitespace-pre-wrap">{change.reason}</p>}
              >
                <ChangeTable
                  rows={
                    kind === 'guarantee'
                      ? changeRows(
                          guaranteeFields,
                          change.before_value as Guarantee | undefined,
                          change.after_value as Guarantee | undefined
                        )
                      : changeRows(
                          bonusFields,
                          change.before_value as Bonus | undefined,
                          change.after_value as Bonus | undefined
                        )
                  }
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
