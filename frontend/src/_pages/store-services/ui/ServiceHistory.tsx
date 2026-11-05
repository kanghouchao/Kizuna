'use client';

import { serviceApi, ServiceSummary, serviceKindLabels } from '@/entities/service';
import { useListPage, isForbidden } from '@/shared/lib';
import {
  ChangeEntry,
  ChangeLog,
  ChangeTable,
  Dialog,
  DialogFrame,
  Pager,
  RegionError,
} from '@/shared/ui';

const fields: [string, (value: ServiceSummary) => string | undefined][] = [
  ['名称', value => value.name],
  ['種別', value => serviceKindLabels[value.kind]],
  [
    '所要時間',
    value => (value.duration_minutes === undefined ? undefined : `${value.duration_minutes} 分`),
  ],
  ['料金区分', value => value.charge_type && (value.charge_type === 'FREE' ? '無料' : '有料')],
  ['価格', value => `${value.price.toLocaleString()} 円`],
  ['固定報酬', value => `${value.remuneration.toLocaleString()} 円`],
  ['状態', value => (value.deleted ? '削除済み' : '有効')],
  ['版本', value => String(value.version)],
];
const show = (
  value: ServiceSummary | undefined,
  read: (value: ServiceSummary) => string | undefined
) => (value && read(value)) ?? '—';
const operationLabels = { CREATED: '作成', UPDATED: '変更', DELETED: '削除' } as const;

interface ServiceHistoryProps {
  id: string;
  open: boolean;
  onClose: () => void;
  onForbidden: () => void;
}

export function ServiceHistory({ id, open, onClose, onForbidden }: ServiceHistoryProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <DialogFrame
        size="lg"
        title="サービス変更履歴"
        description="保存時の変更前後と操作者を、新しい版本から表示します。"
      >
        <HistoryContent id={id} onForbidden={onForbidden} />
      </DialogFrame>
    </Dialog>
  );
}

function HistoryContent({ id, onForbidden }: Pick<ServiceHistoryProps, 'id' | 'onForbidden'>) {
  const history = useListPage(async page => {
    try {
      return await serviceApi.history(id, page);
    } catch (error) {
      if (isForbidden(error)) onForbidden();
      throw error;
    }
  });
  return (
    <>
      {history.failed ? (
        <RegionError message="変更履歴の取得に失敗しました" onRetry={() => void history.reload()} />
      ) : (
        <>
          <ChangeLog>
            {history.rows.map(row => (
              <ChangeEntry
                key={row.id}
                title={`版本 ${row.version} · ${operationLabels[row.operation]}`}
                meta={
                  <p>
                    操作者 ID: {row.actor_id} · {new Date(row.occurred_at).toLocaleString('ja-JP')}
                  </p>
                }
              >
                <ChangeTable
                  rows={fields
                    .filter(([, read]) =>
                      [row.before, row.after].some(v => v && read(v) !== undefined)
                    )
                    .map(([label, read]) => ({
                      label,
                      before: show(row.before, read),
                      after: show(row.after, read),
                    }))}
                />
              </ChangeEntry>
            ))}
          </ChangeLog>
          {history.isLoading ? (
            <p>読み込み中...</p>
          ) : history.rows.length === 0 ? (
            <p>変更履歴はありません</p>
          ) : null}
          <Pager
            page={history.page}
            pageCount={history.pageCount}
            total={history.total}
            rowCount={history.rows.length}
            disabled={history.isLoading}
            onPageChange={page => void history.onPageChange(page)}
          />
        </>
      )}
    </>
  );
}
