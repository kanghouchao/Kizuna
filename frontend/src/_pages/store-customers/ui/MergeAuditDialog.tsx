'use client';

import { customerApi } from '@/entities/customer';
import { useResource } from '@/shared/lib';
import { Dialog, DialogContent, DialogTitle, RegionError } from '@/shared/ui';
import { MergeSnapshotView } from './MergeSnapshotView';

export function MergeAuditDialog({
  customerId,
  mergeId,
  onClose,
}: {
  customerId: string;
  mergeId: string | null;
  onClose: () => void;
}) {
  const { data, isLoading, failure, reload } = useResource(
    mergeId ? () => customerApi.mergeAudit(customerId, mergeId) : null,
    [customerId, mergeId]
  );
  return (
    <Dialog
      open={mergeId !== null}
      onOpenChange={open => {
        if (!open) onClose();
      }}
    >
      <DialogContent
        className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-5xl"
        aria-describedby={undefined}
      >
        <DialogTitle>統合の監査記録</DialogTitle>
        {isLoading ? (
          <p>読み込み中...</p>
        ) : failure ? (
          <RegionError message="監査記録の取得に失敗しました" onRetry={() => void reload()} />
        ) : (
          data && (
            <div className="space-y-6 break-words">
              <dl className="space-y-2 text-sm">
                <dt>統合 ID</dt>
                <dd>{data.id}</dd>
                <dt>理由</dt>
                <dd className="whitespace-pre-wrap">{data.operation_reason}</dd>
                <dt>実行者・日時</dt>
                <dd>
                  {data.merged_by_name ?? '不明'}（ID: {data.merged_by}）· {data.merged_at}
                </dd>
              </dl>
              <div className="grid gap-6 lg:grid-cols-3">
                <MergeSnapshotView title="存続側の原資料" snapshot={data.before_surviving} />
                <MergeSnapshotView title="被統合側の原資料" snapshot={data.before_merged} />
                <MergeSnapshotView title="確定資料" snapshot={data.after_surviving} />
              </div>
              <dl className="space-y-2 text-sm">
                <dt>移動した受注 ID</dt>
                <dd>{data.moved_order_ids.join('、') || 'なし'}</dd>
                <dt>移動した連絡先 ID</dt>
                <dd>{data.moved_contact_ids.join('、') || 'なし'}</dd>
                <dt>移動した関連 ID</dt>
                <dd>{data.moved_link_ids.join('、') || 'なし'}</dd>
              </dl>
            </div>
          )
        )}
      </DialogContent>
    </Dialog>
  );
}
