'use client';

import { serviceApi, ServiceSummary, serviceKindLabels } from '@/entities/service';
import { useCursorList, isForbidden } from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  RegionError,
} from '@/shared/ui';

function Conditions({ value }: { value?: ServiceSummary }) {
  if (!value) return <p>作成前の設定はありません</p>;
  return (
    <dl className="grid grid-cols-2 gap-2 text-sm">
      <dt>名称</dt>
      <dd className="break-all">{value.name}</dd>
      <dt>種別</dt>
      <dd>{serviceKindLabels[value.kind]}</dd>
      {value.duration_minutes !== undefined && (
        <>
          <dt>所要時間</dt>
          <dd>{value.duration_minutes} 分</dd>
        </>
      )}
      {value.charge_type && (
        <>
          <dt>料金区分</dt>
          <dd>{value.charge_type === 'FREE' ? '無料' : '有料'}</dd>
        </>
      )}
      <dt>価格</dt>
      <dd>{value.price.toLocaleString()} 円</dd>
      <dt>固定報酬</dt>
      <dd>{value.remuneration.toLocaleString()} 円</dd>
      <dt>状態</dt>
      <dd>{value.deleted ? '削除済み' : '有効'}</dd>
      <dt>版本</dt>
      <dd>{value.version}</dd>
    </dl>
  );
}
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
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-3xl">
        <HistoryContent id={id} onForbidden={onForbidden} />
      </DialogContent>
    </Dialog>
  );
}

function HistoryContent({ id, onForbidden }: Pick<ServiceHistoryProps, 'id' | 'onForbidden'>) {
  const history = useCursorList(async cursor => {
    try {
      return await serviceApi.history(id, cursor);
    } catch (error) {
      if (isForbidden(error)) onForbidden();
      throw error;
    }
  });
  return (
    <>
      <DialogHeader>
        <DialogTitle>サービス変更履歴</DialogTitle>
        <DialogDescription>
          保存時の変更前後と操作者を、新しい版本から表示します。
        </DialogDescription>
      </DialogHeader>
      {history.failed ? (
        <RegionError message="変更履歴の取得に失敗しました" onRetry={history.reload} />
      ) : (
        <>
          {history.rows.map(row => (
            <section key={row.id} className="space-y-3 rounded-lg border p-4">
              <h2 className="font-semibold">
                版本 {row.version} ·{' '}
                {{ CREATED: '作成', UPDATED: '変更', DELETED: '削除' }[row.operation]}
              </h2>
              <p className="text-sm">
                操作者 ID: {row.actor_id} · {new Date(row.occurred_at).toLocaleString('ja-JP')}
              </p>
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <h3 className="mb-2 font-medium">変更前</h3>
                  <Conditions value={row.before} />
                </div>
                <div>
                  <h3 className="mb-2 font-medium">変更後</h3>
                  <Conditions value={row.after} />
                </div>
              </div>
            </section>
          ))}
          {history.isLoading ? (
            <p>読み込み中...</p>
          ) : history.rows.length === 0 ? (
            <p>変更履歴はありません</p>
          ) : null}
          {history.hasMore && (
            <Button variant="outline" onClick={history.loadMore} disabled={history.isLoading}>
              続きを表示
            </Button>
          )}
        </>
      )}
    </>
  );
}
