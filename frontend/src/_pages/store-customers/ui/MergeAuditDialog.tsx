'use client';

import { customerApi } from '@/entities/customer';
import { useResource } from '@/shared/lib';
import { Dialog, DialogFrame, KeyValueList, KeyValueRow, RegionError } from '@/shared/ui';
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
      <DialogFrame size="lg" title="統合の監査記録">
        {isLoading ? (
          <p>読み込み中...</p>
        ) : failure ? (
          <RegionError message="監査記録の取得に失敗しました" onRetry={() => void reload()} />
        ) : (
          data && (
            <div className="space-y-6 break-words">
              <KeyValueList>
                <KeyValueRow label="統合 ID">{data.id}</KeyValueRow>
                <KeyValueRow label="実行者・日時">
                  {data.merged_by_name ?? '不明'}（ID: {data.merged_by}）· {data.merged_at}
                </KeyValueRow>
                <KeyValueRow label="理由" long>
                  {data.operation_reason}
                </KeyValueRow>
              </KeyValueList>
              <div className="grid gap-6 lg:grid-cols-3">
                <MergeSnapshotView title="存続側の原資料" snapshot={data.before_surviving} />
                <MergeSnapshotView title="被統合側の原資料" snapshot={data.before_merged} />
                <MergeSnapshotView title="確定資料" snapshot={data.after_surviving} />
              </div>
              <KeyValueList>
                <KeyValueRow label="移動した受注 ID">
                  {data.moved_order_ids.join('、') || 'なし'}
                </KeyValueRow>
                <KeyValueRow label="移動した連絡先 ID">
                  {data.moved_contact_ids.join('、') || 'なし'}
                </KeyValueRow>
                <KeyValueRow label="移動した関連 ID">
                  {data.moved_link_ids.join('、') || 'なし'}
                </KeyValueRow>
              </KeyValueList>
            </div>
          )
        )}
      </DialogFrame>
    </Dialog>
  );
}
