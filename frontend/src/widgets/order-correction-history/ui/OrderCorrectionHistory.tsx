'use client';

import { useState } from 'react';
import { orderApi, OrderCorrectionSnapshot, ORDER_FEE_LINE_KIND_LABELS } from '@/entities/order';
import { useCursorList } from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  RegionError,
} from '@/shared/ui';

interface Props {
  orderId: string;
  scope: 'store' | 'platform';
}
const dateTime = (value: string) => new Date(value).toLocaleString('ja-JP');
const yen = (value: number) => `¥${value.toLocaleString()}`;

function Snapshot({ value, label }: { value: OrderCorrectionSnapshot; label: string }) {
  return (
    <section className="min-w-0 space-y-3 rounded-lg border p-4" aria-label={label}>
      <h4 className="font-medium">{label}</h4>
      {value.completion_invalidated && (
        <p>無効化済み。項目の費用・報酬は原記録で、有効額は零です。</p>
      )}
      <p>
        実際の到着 {value.actual_arrival_time ?? '未記録'} / 終了{' '}
        {value.actual_end_time ?? '未記録'}
      </p>
      <p>
        総時間 {value.total_duration_minutes} 分 / 固定報酬 {yen(value.total_remuneration)}
      </p>
      <p>
        {value.course.name} / 版本 {value.course.revision_number} / {value.course.duration_minutes}{' '}
        分
      </p>
      <ul className="space-y-3">
        {value.fee_lines.map((line, i) => (
          <li key={line.line_id ?? i} className="break-words">
            <p>
              {line.name}（{ORDER_FEE_LINE_KIND_LABELS[line.kind]}）
            </p>
            <p>
              費用 {line.kind === 'DISCOUNT' || line.kind === 'POINT_REDEMPTION' ? '−' : ''}
              {yen(line.amount)} / 報酬 {yen(line.remuneration)}
            </p>
            {line.duration_minutes != null && <p>{line.duration_minutes} 分</p>}
            {line.revision_id && (
              <p>
                版本 {line.revision_number}（{line.revision_id}）
              </p>
            )}
            {line.adoption_basis && (
              <p>
                採用根拠{' '}
                {line.adoption_basis === 'HISTORICAL_CORRECTION'
                  ? '歴史版本による訂正'
                  : line.adoption_basis === 'ACCEPTED_TERMS'
                    ? '本人受諾条件'
                    : '現在設定'}
              </p>
            )}
            {line.adopted_at && <p>採用日時 {dateTime(line.adopted_at)}</p>}
            {line.system_owned && <p>システム専有明細</p>}
          </li>
        ))}
      </ul>
      {value.special_services.map(item => (
        <p className="break-words" key={item.service_id}>
          {item.name} / 条件版本 {item.terms_version} / 在籍 {item.enrollment_id}
          {item.consent_event_id
            ? ` / 受諾記録 ${item.consent_event_id}（版本 ${item.consent_version}）`
            : ' / 歴史版本による訂正'}
        </p>
      ))}
    </section>
  );
}

export function OrderCorrectionHistory({ orderId, scope }: Props) {
  const [status, setStatus] = useState<number>();
  const history = useCursorList(cursor =>
    orderApi.correctionHistory(scope, orderId, cursor).catch(error => {
      setStatus(error?.response?.status);
      throw error;
    })
  );
  if (history.failed)
    return (
      <RegionError
        message={
          status === 403
            ? '履歴を閲覧する権限がありません。'
            : status === 404
              ? '受注が見つからないか、閲覧範囲外です。'
              : '履歴を取得できませんでした。'
        }
        {...(status === 404
          ? {
              fallback: {
                href: scope === 'platform' ? '/platform/orders' : '/store/entry',
                label: '一覧へ戻る',
              },
            }
          : { onRetry: history.reload })}
      />
    );
  return (
    <div className="space-y-6 text-sm">
      <p className="text-muted-foreground">
        提供内容の訂正履歴です。報酬は発生額であり、支払済み額ではありません。ポイント・会員帰属・実入出金はこの訂正で変わりません。
      </p>
      {history.rows.map(entry => (
        <article key={entry.correction_id} className="space-y-3 border-b pb-6">
          <h3 className="font-medium break-words">{entry.reason}</h3>
          <p>
            原営業日 {entry.business_date} / 原完了日時 {dateTime(entry.completed_at)}
          </p>
          <p>
            {entry.change_type === 'COMPLETION_INVALIDATION' ? '誤完了の無効化日時' : '訂正日時'}{' '}
            {dateTime(entry.corrected_at)} / 操作者 {entry.corrected_by ?? '削除済み利用者'}
          </p>
          <p className="break-words">
            訂正 ID {entry.correction_id} / 受注版 {entry.before_version} → {entry.after_version}
          </p>
          <p>
            請求 {yen(entry.before.total_fee)} → {yen(entry.after.total_fee)}
          </p>
          <p>
            発生済み報酬 {yen(entry.before.accrued_remuneration)} →{' '}
            {yen(entry.after.accrued_remuneration)}
          </p>
          <div className="grid gap-4 md:grid-cols-2">
            <Snapshot value={entry.before} label="訂正前" />
            <Snapshot value={entry.after} label="訂正後" />
          </div>
        </article>
      ))}
      {history.isLoading && <p role="status">履歴を読み込み中...</p>}
      {!history.isLoading && history.rows.length === 0 && <p>訂正履歴はありません</p>}
      {history.hasMore && (
        <Button variant="outline" disabled={history.isLoading} onClick={history.loadMore}>
          さらに表示
        </Button>
      )}
    </div>
  );
}

export function OrderCorrectionHistoryDialog(props: Props) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)}>
        訂正履歴
      </Button>
      <OrderCorrectionHistoryModal {...props} open={open} onOpenChange={setOpen} />
    </>
  );
}

export function OrderCorrectionHistoryModal({
  open,
  onOpenChange,
  ...props
}: Props & { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>訂正履歴</DialogTitle>
          <DialogDescription>受注 {props.orderId} の費用・報酬の変更記録</DialogDescription>
        </DialogHeader>
        {open && <OrderCorrectionHistory key={`${props.scope}:${props.orderId}`} {...props} />}
      </DialogContent>
    </Dialog>
  );
}
