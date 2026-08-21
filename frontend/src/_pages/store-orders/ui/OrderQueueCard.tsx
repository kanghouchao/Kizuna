'use client';

import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { CircleCheckIcon, SquarePenIcon, XIcon } from 'lucide-react';
import {
  ORDER_STATUS_LABELS,
  OrderWorkQueueRow,
  WEB_APPLICATION_ROUTE_LABELS,
  isWebApplicationRoute,
  orderApi,
} from '@/entities/order';
import { getApiErrorMessage, requireId, storePath } from '@/shared/lib';
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
  /** 取消のように、この受注が群から外れる処理が終わったとき。 */
  onProcessed: (id: string) => void;
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
 * 対応が要る受注 1 件のカード。完了・取消をその場で担い、編集は専用の頁へ送る（未処理の予約申請は受付箱のカードが受け持つ）。
 *
 * <p>取消はモーダルを開かず、カードがその場で理由入力に変わる（二段）。一覧の中で完結させるのが この画面の主張なので、確認だけを外へ出さない。
 */
export function OrderQueueCard({ order, onProcessed, onComplete }: OrderQueueCardProps) {
  const params = useParams();
  const storeId = params.storeId as string;
  const router = useRouter();
  const [processing, setProcessing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const cancelForm = useForm<{ reason: string }>({ defaultValues: { reason: '' } });

  /**
   * 処理を走らせて結果を通知する。処理後にこの受注が群から外れるのか、群の中で状態が変わるだけなのかは
   * 操作ごとに違うので、行の始末は {@code settle} が決める。
   *
   * <p>ここで識別子を解くのは URI のためではない（それはアダプタの仕事）。群から外す行を名指すのに
   * 確かな識別子が要るからで、名指せないなら手を付ける前に止める。
   */
  const run = async <T,>(
    action: (id: string) => Promise<T>,
    success: string,
    failure: string,
    settle: (updated: T, id: string) => void
  ) => {
    setProcessing(true);
    try {
      const id = requireId(order.id, '受注');
      const updated = await action(id);
      notify.success(success);
      settle(updated, id);
    } catch (error) {
      // 指名の再検証や終端の凍結など、サーバは対処方法を含む文言を返す。汎用文言に潰さない
      notify.error(getApiErrorMessage(error, failure));
    } finally {
      setProcessing(false);
    }
  };

  /** 取消の後始末。この受注はもう「対応が要る」群の対象ではないので手元から取り除く。 */
  const leaveQueue = (_updated: unknown, id: string) => onProcessed(id);

  return (
    <li className="bg-card space-y-3 rounded-lg border p-4 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <CustomerName order={order} />
            <Badge
              variant="outline"
              className="border-transparent bg-success/10 text-success-strong"
            >
              {ORDER_STATUS_LABELS[order.status ?? 'CONFIRMED']}
            </Badge>
            {/* 表示を持つ経路だけを出す。「電話でない」で選ると、経路が増えた日に名札の無い値が入り込む */}
            {order.reception_route && isWebApplicationRoute(order.reception_route) && (
              <Badge
                variant="outline"
                className="border-transparent bg-primary/10 text-primary-strong"
              >
                {WEB_APPLICATION_ROUTE_LABELS[order.reception_route]}
              </Badge>
            )}
            <span className="text-muted-foreground text-sm">{order.business_date}</span>
          </div>
          <CardMeta order={order} />
          {order.remarks && <p className="text-muted-foreground text-xs">{order.remarks}</p>}
        </div>

        {!cancelling && (
          <div className="flex shrink-0 flex-wrap items-center justify-end gap-1">
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
              onClick={() => router.push(storePath(storeId, `/orders/${order.id}/edit`))}
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
                id => orderApi.cancel(id, { reason: values.reason.trim() }),
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
