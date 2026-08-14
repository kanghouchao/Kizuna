'use client';

import { useState } from 'react';
import { CircleCheckIcon, SquarePenIcon, XIcon } from 'lucide-react';
import { ORDER_STATUS_LABELS, Order, orderApi } from '@/entities/order';
import { getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { UNLINKED_NOTE, customerLabel } from '../lib/customerLabel';
import { Badge, Button, Label, Textarea } from '@/shared/ui';

/** 取消の理由の上限。サーバ側の列長（500）と揃える。 */
const CANCEL_REASON_MAX_LENGTH = 500;

interface OrderQueueCardProps {
  order: Order;
  /** 確定・謝絶・取消のように、この受注が群から外れる処理が終わったとき。 */
  onProcessed: (id: string) => void;
  /** 編集モーダルを開く。 */
  onEdit: (order: Order) => void;
  /** 完了モーダルを開く。 */
  onComplete: (order: Order) => void;
}

function CustomerName({ order }: { order: Order }) {
  const label = customerLabel(order);
  if (label === null) {
    return <span className="text-muted-foreground">お客様名なし</span>;
  }
  return (
    <>
      <span className="text-foreground font-medium">{label.name}</span>
      {label.unlinked && <span className="text-muted-foreground text-sm">{UNLINKED_NOTE}</span>}
    </>
  );
}

/** カードの 2 行目。時刻・指名・人数・コース・受付を 1 本の注記行に畳む。 */
function CardMeta({ order }: { order: Order }) {
  const parts = [
    order.arrival_scheduled_start_time?.slice(0, 5) ?? '時刻未定',
    order.cast_name ? `指名 ${order.cast_name}` : 'フリー',
    order.pax != null ? `${order.pax} 名` : null,
    order.course_minutes != null ? `${order.course_minutes} 分` : null,
    order.receptionist_name ? `受付 ${order.receptionist_name}` : null,
  ].filter(Boolean);
  return <p className="text-muted-foreground text-sm">{parts.join(' ・ ')}</p>;
}

/**
 * 対応が要る受注 1 件のカード。
 *
 * <p>未確定（会員申請）は確定・謝絶・申請編集を、確定済みは完了・編集・取消を担う。受付箱を別に持たず この 1 枚に寄せているのは、同じ申請を 2
 * 箇所で見比べさせないため。
 *
 * <p>取消はモーダルを開かず、カードがその場で理由入力に変わる（二段）。一覧の中で完結させるのが この画面の主張なので、確認だけを外へ出さない。
 */
export function OrderQueueCard({ order, onProcessed, onEdit, onComplete }: OrderQueueCardProps) {
  const [processing, setProcessing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');

  const id = order.id ?? '';
  const pending = order.status === 'CREATED';

  const run = async (action: () => Promise<unknown>, success: string, failure: string) => {
    setProcessing(true);
    try {
      await action();
      notify.success(success);
      // 処理し終えた受注はこの群の対象から外れるので、手元から取り除くだけで一覧は正しくなる
      onProcessed(id);
    } catch (error) {
      // 指名の再検証や終端の凍結など、サーバは対処方法を含む文言を返す。汎用文言に潰さない
      notify.error(getApiErrorMessage(error, failure));
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className="bg-card space-y-3 rounded-lg border p-4 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <CustomerName order={order} />
            <Badge
              variant="outline"
              className={
                pending
                  ? 'border-transparent bg-muted text-foreground'
                  : 'border-transparent bg-success/10 text-success-strong'
              }
            >
              {ORDER_STATUS_LABELS[order.status ?? 'CREATED']}
            </Badge>
            {/* 申請の判定は受付経路だけでは足りない。サーバ側と同じく申請者の有無まで見る */}
            {order.reception_route === 'WEB' && order.requester_member_code && (
              <Badge
                variant="outline"
                className="border-transparent bg-primary/10 text-primary-strong"
              >
                WEB申請
              </Badge>
            )}
            <span className="text-muted-foreground text-sm">{order.business_date}</span>
          </div>
          <CardMeta order={order} />
          {pending && order.requester_member_code && (
            <p className="text-muted-foreground text-xs">
              会員コード: {order.requester_member_code}
            </p>
          )}
          {order.remarks && <p className="text-muted-foreground text-xs">{order.remarks}</p>}
        </div>

        {!cancelling && (
          <div className="flex shrink-0 flex-wrap items-center justify-end gap-1">
            {pending ? (
              <>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={processing}
                  onClick={() => onEdit(order)}
                >
                  <SquarePenIcon aria-hidden="true" />
                  編集
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={processing}
                  onClick={() =>
                    run(() => orderApi.decline(id), '予約を謝絶しました', '謝絶に失敗しました')
                  }
                >
                  謝絶
                </Button>
                <Button
                  type="button"
                  size="sm"
                  disabled={processing}
                  onClick={() =>
                    run(() => orderApi.confirm(id), '予約を確定しました', '確定に失敗しました')
                  }
                >
                  確定
                </Button>
              </>
            ) : (
              <>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={processing}
                  onClick={() => onComplete(order)}
                >
                  <CircleCheckIcon aria-hidden="true" />
                  完了
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={processing}
                  onClick={() => onEdit(order)}
                >
                  <SquarePenIcon aria-hidden="true" />
                  編集
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-destructive-strong"
                  disabled={processing}
                  onClick={() => setCancelling(true)}
                >
                  <XIcon aria-hidden="true" />
                  取消
                </Button>
              </>
            )}
          </div>
        )}
      </div>

      {cancelling && (
        <div className="border-destructive/40 space-y-3 rounded-lg border p-3">
          <p className="text-destructive-strong text-sm">
            取消した受注は元に戻せません。理由は記録に残ります。
          </p>
          <div className="space-y-2">
            <Label htmlFor={`cancel-reason-${id}`}>取消の理由</Label>
            <Textarea
              id={`cancel-reason-${id}`}
              rows={2}
              required
              maxLength={CANCEL_REASON_MAX_LENGTH}
              value={reason}
              onChange={e => setReason(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={processing}
              onClick={() => {
                setReason('');
                setCancelling(false);
              }}
            >
              やめる
            </Button>
            <Button
              type="button"
              variant="destructive"
              size="sm"
              // 理由が無い取消はサーバも撥ねる。押せてしまうと、書かずに済むと誤解させる
              disabled={processing || reason.trim().length === 0}
              onClick={() =>
                run(
                  () => orderApi.cancel(id, { reason: reason.trim() }),
                  '受注を取消しました',
                  '取消に失敗しました'
                )
              }
            >
              取消する
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
