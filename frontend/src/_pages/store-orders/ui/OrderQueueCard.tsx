'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { CircleCheckIcon, SquarePenIcon, XIcon } from 'lucide-react';
import { ORDER_STATUS_LABELS, OrderWorkQueueRow, orderApi } from '@/entities/order';
import { getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { UNLINKED_NOTE, customerLabel } from '../lib/customerLabel';
import {
  Badge,
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Textarea,
} from '@/shared/ui';

/** 取消の理由の上限。サーバ側の列長（500）と揃える。 */
const CANCEL_REASON_MAX_LENGTH = 500;

interface OrderQueueCardProps {
  order: OrderWorkQueueRow;
  /** 謝絶・取消のように、この受注が群から外れる処理が終わったとき。 */
  onProcessed: (id: string) => void;
  /**
   * 確定が終わったとき。確定は「対応が要る」群の中の移動（未確定 → 確定）で群からは外れないため、
   * 取り除かずに応答の内容へ差し替える。
   */
  onConfirmed: (confirmed: OrderWorkQueueRow) => void;
  /** 編集モーダルを開く。 */
  onEdit: (order: OrderWorkQueueRow) => void;
  /** 完了モーダルを開く。 */
  onComplete: (order: OrderWorkQueueRow) => void;
}

function CustomerName({ order }: { order: OrderWorkQueueRow }) {
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
function CardMeta({ order }: { order: OrderWorkQueueRow }) {
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
export function OrderQueueCard({
  order,
  onProcessed,
  onConfirmed,
  onEdit,
  onComplete,
}: OrderQueueCardProps) {
  const [processing, setProcessing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const cancelForm = useForm<{ reason: string }>({ defaultValues: { reason: '' } });

  const id = order.id ?? '';
  const pending = order.status === 'CREATED';

  /**
   * 処理を走らせて結果を通知する。処理後にこの受注が群から外れるのか、群の中で状態が変わるだけなのかは
   * 操作ごとに違うので、行の始末は {@code settle} が決める。
   */
  const run = async <T,>(
    action: () => Promise<T>,
    success: string,
    failure: string,
    settle: (updated: T) => void
  ) => {
    setProcessing(true);
    try {
      const updated = await action();
      notify.success(success);
      settle(updated);
    } catch (error) {
      // 指名の再検証や終端の凍結など、サーバは対処方法を含む文言を返す。汎用文言に潰さない
      notify.error(getApiErrorMessage(error, failure));
    } finally {
      setProcessing(false);
    }
  };

  /** 謝絶・取消の後始末。この受注はもう「対応が要る」群の対象ではないので手元から取り除く。 */
  const leaveQueue = () => onProcessed(id);

  return (
    <li className="bg-card space-y-3 rounded-lg border p-4 shadow-sm">
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
                    run(
                      () => orderApi.decline(id),
                      '予約を謝絶しました',
                      '謝絶に失敗しました',
                      leaveQueue
                    )
                  }
                >
                  謝絶
                </Button>
                <Button
                  type="button"
                  size="sm"
                  disabled={processing}
                  onClick={() =>
                    // 確定した受注は「対応が要る」群に残る（次は完了・編集・取消の対象）。
                    // 応答へ差し替えるのは、確定が受付担当の補完と顧客の着け直しまで行うため。
                    run(
                      () => orderApi.confirm(id),
                      '予約を確定しました',
                      '確定に失敗しました',
                      onConfirmed
                    )
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
        <Form {...cancelForm}>
          {/* native の検証が割り込むと、こちらの文言が描かれないまま送信が止まる */}
          <form
            noValidate
            onSubmit={cancelForm.handleSubmit(values =>
              run(
                () => orderApi.cancel(id, { reason: values.reason.trim() }),
                '受注を取消しました',
                '取消に失敗しました',
                leaveQueue
              )
            )}
            className="border-destructive/40 space-y-3 rounded-lg border p-3"
          >
            <p className="text-destructive-strong text-sm">
              取消した受注は元に戻せません。理由は記録に残ります。
            </p>
            <FormField
              control={cancelForm.control}
              name="reason"
              // 理由は取消の根拠そのもの。空白だけは書いていないのと同じ（サーバも同じに撥ねる）
              rules={{
                validate: value => value.trim().length > 0 || '取消の理由を入力してください',
                maxLength: {
                  value: CANCEL_REASON_MAX_LENGTH,
                  message: `取消の理由は ${CANCEL_REASON_MAX_LENGTH} 文字以内です`,
                },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>取消の理由</FormLabel>
                  <FormControl>
                    {/* required は検証ではなく支援技術への告知として残す（規則の側が enforcement） */}
                    <Textarea rows={2} required maxLength={CANCEL_REASON_MAX_LENGTH} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={processing}
                onClick={() => {
                  cancelForm.reset({ reason: '' });
                  setCancelling(false);
                }}
              >
                やめる
              </Button>
              {/* 検証では塞がない — 灰色のボタンは何が足りないかを言わない。押せば欄の傍が言う */}
              <Button type="submit" variant="destructive" size="sm" disabled={processing}>
                取消する
              </Button>
            </div>
          </form>
        </Form>
      )}
    </li>
  );
}
