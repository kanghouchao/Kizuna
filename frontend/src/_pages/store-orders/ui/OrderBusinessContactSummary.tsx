import { useState } from 'react';
import { BusinessContactPermission, orderApi } from '@/entities/order';
import { useCursorList } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
import { CONTACT_CHANNELS, CONTACT_STATUS_LABELS } from './OrderBusinessContactFields';

export function OrderBusinessContactSummary({
  permissions,
  orderId,
}: {
  permissions: BusinessContactPermission[];
  orderId: string;
}) {
  const [showHistory, setShowHistory] = useState(false);
  return (
    <section className="space-y-4 rounded-xl border bg-card p-6 text-card-foreground">
      <h2 className="font-semibold">保存済みの今回の業務連絡可否</h2>
      {permissions.map(permission => (
        <div key={permission.type} className="space-y-2 break-words">
          <p>
            {CONTACT_CHANNELS.find(c => c.type === permission.type)?.label}：{permission.value} —{' '}
            {CONTACT_STATUS_LABELS[permission.status]}
          </p>
          <p>
            {permission.source ?? '出所未記録'}：{permission.reason ?? '根拠未記録'}
          </p>
          {permission.recorded_at && (
            <p className="text-sm text-muted-foreground">
              記録：{new Date(permission.recorded_at).toLocaleString('ja-JP')} ／ 操作者：
              {permission.recorded_by ?? '削除済み'}
            </p>
          )}
          <p
            className={
              permission.decision === 'STORE_DENIED'
                ? 'text-destructive-strong'
                : 'text-muted-foreground'
            }
          >
            {permission.decision === 'STORE_DENIED'
              ? '同店の顧客台帳に拒否があるため連絡できません。台帳の担当者による新しい根拠付きの変更が必要です。'
              : permission.decision === 'ALLOWED'
                ? '現在の判定：業務連絡可。送信・再送直前に再判定します。'
                : '現在の判定：業務連絡不可。'}
          </p>
        </div>
      ))}
      {permissions.length === 0 && <p>連絡先は未設定です。</p>}
      <Button type="button" variant="outline" onClick={() => setShowHistory(!showHistory)}>
        {showHistory ? '連絡可否の履歴を閉じる' : '連絡可否の履歴を表示'}
      </Button>
      {showHistory && <History orderId={orderId} />}
    </section>
  );
}
function History({ orderId }: { orderId: string }) {
  const list = useCursorList(cursor => orderApi.businessContactHistory(orderId, cursor));
  return (
    <div className="space-y-4">
      {list.rows.map(row => (
        <div key={row.id} className="space-y-1 break-words rounded-lg border p-4">
          <p>
            {CONTACT_CHANNELS.find(channel => channel.type === row.type)?.label} ／{' '}
            {new Date(row.recorded_at).toLocaleString('ja-JP')} ／{' '}
            {row.action === 'RECORDED' ? '明示記録' : '宛先変更'} ／ 操作者：
            {row.recorded_by ?? '削除済み'}
          </p>
          <p>
            変更前：
            {row.before
              ? `${row.before.value}・${CONTACT_STATUS_LABELS[row.before.status]}・${row.before.source ?? '出所未記録'}・${row.before.reason ?? '根拠未記録'}`
              : '連絡先なし'}
          </p>
          <p>
            変更後：
            {row.after
              ? `${row.after.value}・${CONTACT_STATUS_LABELS[row.after.status]}・${row.after.source ?? '出所未記録'}・${row.after.reason ?? '根拠未記録'}`
              : '連絡先なし'}
          </p>
        </div>
      ))}
      {list.isLoading && <p>履歴を読み込み中...</p>}
      {list.failed && (
        <RegionError message="連絡可否の履歴を取得できませんでした。" onRetry={list.reload} />
      )}
      {!list.isLoading && !list.failed && list.rows.length === 0 && <p>履歴はありません。</p>}
      {list.hasMore && (
        <Button type="button" variant="outline" disabled={list.isLoading} onClick={list.loadMore}>
          続きを表示
        </Button>
      )}
    </div>
  );
}
