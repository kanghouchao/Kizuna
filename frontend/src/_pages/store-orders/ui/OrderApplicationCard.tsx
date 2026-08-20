'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { CircleCheckIcon } from 'lucide-react';
import { OrderApplicationRow, orderApplicationApi } from '@/entities/order';
import { getApiErrorMessage, requireId } from '@/shared/lib';
import { notify } from '@/shared/notify';
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

/** 謝絶の理由の上限。サーバ側の列長（500）と揃える。 */
const DECLINE_REASON_MAX_LENGTH = 500;

interface OrderApplicationCardProps {
  application: OrderApplicationRow;
  /** 謝絶が終わったとき。この申請はもう受付箱の対象ではないので手元から取り除く。 */
  onDeclined: (id: string) => void;
  /** 確定モーダルを開く。 */
  onConfirm: (application: OrderApplicationRow) => void;
}

/** カードの 2 行目。時刻・指名・人数を 1 本の注記行に畳む。 */
function CardMeta({ application }: { application: OrderApplicationRow }) {
  const parts = [
    application.arrival_scheduled_start_time?.slice(0, 5) ?? '時刻未定',
    application.cast_name ? `指名 ${application.cast_name}` : 'フリー',
    application.pax != null ? `${application.pax} 名` : null,
  ].filter(Boolean);
  return <p className="text-muted-foreground text-sm">{parts.join(' ・ ')}</p>;
}

/**
 * 未処理の予約申請 1 件のカード。確定（受注の作成）と謝絶を担う。
 *
 * <p>ここに出るのは申請原文で、確定時の調整は受注側にだけ現れる（原文は対照のため不変のまま残る）。
 *
 * <p>希望日を過ぎた申請は失効で、確定・謝絶はサーバ側でも拒否される。押せないボタンを出し続けない。
 */
export function OrderApplicationCard({
  application,
  onDeclined,
  onConfirm,
}: OrderApplicationCardProps) {
  const [processing, setProcessing] = useState(false);
  const [declining, setDeclining] = useState(false);
  const declineForm = useForm<{ reason: string }>({ defaultValues: { reason: '' } });

  const decline = async (reason: string) => {
    setProcessing(true);
    try {
      const id = requireId(application.id, '予約申請');
      await orderApplicationApi.decline(id, { reason });
      notify.success('予約申請を謝絶しました');
      onDeclined(id);
    } catch (error) {
      // 失効や処理済みなど、サーバは対処方法を含む文言を返す。汎用文言に潰さない
      notify.error(getApiErrorMessage(error, '謝絶に失敗しました'));
    } finally {
      setProcessing(false);
    }
  };

  return (
    <li className="bg-card space-y-3 rounded-lg border p-4 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            {application.requester_declared_name ? (
              <span className="text-foreground font-medium">
                {application.requester_declared_name}
              </span>
            ) : (
              <span className="text-muted-foreground">お名前なし</span>
            )}
            {application.expired ? (
              <Badge
                variant="outline"
                className="border-transparent bg-muted text-muted-foreground"
              >
                失効
              </Badge>
            ) : (
              <Badge
                variant="outline"
                className="border-transparent bg-warning/10 text-warning-strong"
              >
                申請中
              </Badge>
            )}
            <span className="text-muted-foreground text-sm">{application.business_date}</span>
          </div>
          <CardMeta application={application} />
          {application.requester_member_code && (
            <p className="text-muted-foreground text-xs">
              会員コード: {application.requester_member_code}
            </p>
          )}
          {application.remarks && (
            <p className="text-muted-foreground text-xs">{application.remarks}</p>
          )}
        </div>

        {/* 失効した申請は操作を出さない — サーバも拒否する。行は導出のまま残り、期日が意味を持たない */}
        {!declining && !application.expired && (
          <div className="flex shrink-0 flex-wrap items-center justify-end gap-1">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={processing}
              onClick={() => setDeclining(true)}
            >
              謝絶
            </Button>
            <Button
              type="button"
              size="sm"
              disabled={processing}
              onClick={() => onConfirm(application)}
            >
              <CircleCheckIcon aria-hidden="true" />
              確定
            </Button>
          </div>
        )}
      </div>

      {declining && (
        <Form {...declineForm}>
          {/* native の検証が割り込むと、こちらの文言が描かれないまま送信が止まる */}
          <form
            noValidate
            onSubmit={declineForm.handleSubmit(values => decline(values.reason.trim()))}
            className="border-destructive/40 space-y-3 rounded-lg border p-3"
          >
            <p className="text-destructive-strong text-sm">
              謝絶した申請は元に戻せません。理由は記録に残ります。
            </p>
            <FormField
              control={declineForm.control}
              name="reason"
              // 理由は謝絶の根拠そのもの。空白だけは書いていないのと同じ（サーバも同じに撥ねる）
              rules={{
                validate: value => value.trim().length > 0 || '謝絶の理由を入力してください',
                maxLength: {
                  value: DECLINE_REASON_MAX_LENGTH,
                  message: `謝絶の理由は ${DECLINE_REASON_MAX_LENGTH} 文字以内です`,
                },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>謝絶の理由</FormLabel>
                  <FormControl>
                    {/* required は検証ではなく支援技術への告知として残す（規則の側が enforcement） */}
                    <Textarea rows={2} required maxLength={DECLINE_REASON_MAX_LENGTH} {...field} />
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
                  declineForm.reset({ reason: '' });
                  setDeclining(false);
                }}
              >
                やめる
              </Button>
              {/* 検証では塞がない — 灰色のボタンは何が足りないかを言わない。押せば欄の傍が言う */}
              <Button type="submit" variant="destructive" size="sm" disabled={processing}>
                謝絶する
              </Button>
            </div>
          </form>
        </Form>
      )}
    </li>
  );
}
