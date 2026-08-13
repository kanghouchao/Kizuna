'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { QRCodeSVG } from 'qrcode.react';
import { notify } from '@/shared/notify';
import { Order, OrderAttribution, orderApi } from '@/entities/order';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import { UNLINKED_NOTE, customerLabel } from '../lib/customerLabel';
import { receiptClaimUrl } from '../lib/receiptClaimUrl';
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  RegionError,
  Textarea,
} from '@/shared/ui';

/** 理由の上限。帰属記録側の @Size(max = 500) と同値。 */
const REASON_MAX_LENGTH = 500;

/** 成立の機構の日本語表示。値そのものは事実の記録で、挙動は分けない。 */
const SOURCE_LABELS: Record<string, string> = {
  COMPLETION: '完了時の会員紐づけ',
  RECEIPT_TOKEN: '伝票QRの申領',
};

interface InvalidationFormValues {
  reason: string;
}

/**
 * 取得した帰属の現況と、その取得元の受注。取得フックは取り直しの間も前の値を保つため、
 * 値だけでは別の受注の現況と見分けられない。
 */
interface AttributionSnapshot {
  orderId: string;
  body: OrderAttribution;
}

/** 再発行された伝票トークンと、その発行元の受注（現況と同じ理由で、値だけでは別の受注のものと見分けられない）。 */
interface ReissuedReceiptToken {
  orderId: string;
  token: string;
}

interface OrderAttributionModalProps {
  /** 訂正の対象。null なら閉じている。 */
  order: Order | null;
  onClose: () => void;
}

function formatDateTime(value?: string): string {
  return value ? new Date(value).toLocaleString('ja-JP') : '-';
}

/**
 * 誤帰属の訂正モーダル（帰属記録の無効化と、伝票QRの再発行）。
 *
 * 無効化は帰属記録に対する唯一の訂正操作で、行は削除されず理由・実行者・時刻が残る。
 * ポイント台帳へは波及しないため、誤って付与されたポイントの清算は顧客側のポイント調整で
 * 別途行う必要がある — その旨は取り消せない操作の手前で画面が名乗る。
 *
 * 無効化された受注は「会員へ帰属しない状態」へ戻り、正しい本人は再発行された QR の所持証明で
 * 来店を取り戻せる。申領期限は再発行から 90 日で数え直される。
 */
export function OrderAttributionModal({ order, onClose }: OrderAttributionModalProps) {
  const form = useForm<InvalidationFormValues>({ defaultValues: { reason: '' } });
  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;

  const orderId = order?.id ?? '';
  // 閉じている間は取りに行かない（開いた時点で取り直す）
  const {
    data: snapshot,
    setData: setSnapshot,
    isLoading,
    failure,
    reload,
  } = useResource<AttributionSnapshot>(
    order === null ? null : async () => ({ orderId, body: await orderApi.attribution(orderId) }),
    [orderId]
  );
  // 別の受注へ切り替わった瞬間は現況を持たない状態から始める（レンダー期の判定なので、前の受注の
  // 帰属で無効化・再発行の可否を決めるフレームが 1 つも無い）。
  const attribution = snapshot !== null && snapshot.orderId === orderId ? snapshot.body : null;

  const [reissued, setReissued] = useState<ReissuedReceiptToken | null>(null);
  const receiptToken = reissued !== null && reissued.orderId === orderId ? reissued.token : null;
  const [isReissuing, setIsReissuing] = useState(false);

  useEffect(() => {
    if (!order) return;
    reset({ reason: '' });
    // 発行済みの QR も一緒に戻す。「今だけ表示できる」と書いた画面が、開き直しで前の QR を出し直さない
    setReissued(null);
  }, [order, reset]);

  const invalidate = async (values: InvalidationFormValues) => {
    if (!order?.id) return;
    try {
      const updated = await orderApi.invalidateAttribution(order.id, { reason: values.reason });
      notify.success('帰属を無効化しました');
      // 応答が訂正後の現況を持つので、取り直さず差し替える（読み込み表示で一瞬消えない）
      setSnapshot({ orderId, body: updated });
      reset({ reason: '' });
    } catch (error) {
      // 帰属していない・既に無効化済みは、サーバが対処できる文言を返す。汎用文言に潰さない。
      notify.error(getApiErrorMessage(error, '帰属の無効化に失敗しました'));
    }
  };

  const reissue = async () => {
    if (!order?.id) return;
    try {
      setIsReissuing(true);
      const issued = await orderApi.reissueReceiptToken(order.id);
      notify.success('伝票QRを再発行しました');
      setReissued({ orderId, token: issued.receipt_token });
    } catch (error) {
      notify.error(getApiErrorMessage(error, '伝票QRの再発行に失敗しました'));
    } finally {
      setIsReissuing(false);
    }
  };

  const isBusy = isSubmitting || isReissuing;

  return (
    <Dialog
      open={order !== null}
      onOpenChange={next => {
        // 送信中に閉じると、訂正が成立したかどうか分からないまま古い現況が残る。
        // QR を出している間も同じく閉じない — 生値はこの応答にしか無く、ESC や背景押下で
        // 誤って閉じると客が来店を取り戻す手段ごと消える。閉じるのは明示のボタンだけにする。
        if (!next && !isBusy && receiptToken === null) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <div className="border-b px-6 py-4">
          <DialogTitle>{receiptToken !== null ? '伝票QRコード' : '会員帰属の訂正'}</DialogTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            {order?.business_date ?? '-'} / {customerText(order)}
          </p>
        </div>
        {receiptToken !== null ? (
          <div className="px-6 py-5">
            <div className="flex flex-col items-center gap-4">
              <div className="rounded-xl border bg-card p-4">
                <QRCodeSVG
                  value={receiptClaimUrl(receiptToken)}
                  size={192}
                  aria-label="伝票QR"
                  role="img"
                />
              </div>
              {/* 生値はこの応答にしか現れない。閉じた後に出し直す経路が無いことを先に伝える */}
              <p className="text-sm text-foreground">
                この QR は今だけ表示できます。閉じると再表示できません。
              </p>
              <p className="text-sm text-muted-foreground">
                正しいお客様が読み取ると、この来店をご自身のポイントと履歴に取り込めます（再発行から
                90 日以内）。
              </p>
            </div>
            <div className="mt-4 flex justify-end gap-3 border-t pt-4">
              <Button type="button" onClick={onClose}>
                閉じる
              </Button>
            </div>
          </div>
        ) : isLoading ? (
          <p className="px-6 py-8 text-center text-sm text-muted-foreground">読み込み中...</p>
        ) : failure !== null ? (
          // 読めなかった現況を「未帰属」で描くと、他人の来店が残っているのに再発行を勧めることになる
          <RegionError
            message="帰属の状況を取得できませんでした"
            onRetry={() => void reload()}
            className="justify-center px-6 py-8"
          />
        ) : (
          attribution !== null && (
            <div className="space-y-4 px-6 py-5">
              <div className="space-y-1 text-sm text-foreground">
                {attribution.attributed ? (
                  <>
                    <div className="flex items-center gap-2">
                      <Badge
                        variant="outline"
                        className="border-transparent bg-success/10 text-success-strong"
                      >
                        帰属済み
                      </Badge>
                      <span>{attribution.member_code}</span>
                    </div>
                    <p className="text-muted-foreground">
                      {SOURCE_LABELS[attribution.source ?? ''] ?? attribution.source} /{' '}
                      {formatDateTime(attribution.attributed_at)}
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-muted-foreground">この来店は会員へ帰属していません。</p>
                    {attribution.member_code && (
                      <p className="text-muted-foreground">
                        {attribution.member_code} への帰属を
                        {formatDateTime(attribution.invalidated_at)}に無効化（
                        {attribution.invalidated_reason}）
                      </p>
                    )}
                  </>
                )}
              </div>

              {attribution.attributed ? (
                <Form {...form}>
                  {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
                      我々の文言は永久に描かれない。required は下の規則が引き継ぐ */}
                  <form onSubmit={handleSubmit(invalidate)} className="space-y-4" noValidate>
                    <FormField
                      control={control}
                      name="reason"
                      rules={{
                        required: '無効化の理由を入力してください',
                        maxLength: {
                          value: REASON_MAX_LENGTH,
                          message: `理由は${REASON_MAX_LENGTH}文字以内で入力してください`,
                        },
                      }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>無効化の理由</FormLabel>
                          <FormControl>
                            <Textarea rows={3} required maxLength={REASON_MAX_LENGTH} {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    {/* 台帳へ波及しないことは操作の前に知らせる。事後に気づくと、誤付与が残ったまま
                        訂正が済んだと見なされる */}
                    <p className="text-sm text-muted-foreground">
                      無効化しても付与済みのポイントは戻りません。清算が必要な場合は顧客画面のポイント調整で行ってください。
                    </p>
                    <div className="flex justify-end gap-3 border-t pt-4">
                      <Button type="button" variant="outline" onClick={onClose} disabled={isBusy}>
                        キャンセル
                      </Button>
                      <Button type="submit" disabled={isBusy}>
                        {isSubmitting ? '処理中...' : '無効化する'}
                      </Button>
                    </div>
                  </form>
                </Form>
              ) : (
                <>
                  {attribution.member_code ? (
                    <p className="text-sm text-muted-foreground">
                      伝票QRを再発行すると、正しいお客様が読み取ってこの来店を取り戻せます。
                    </p>
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      この受注は会員へ帰属したことがないため、訂正の対象ではありません。
                    </p>
                  )}
                  <div className="flex justify-end gap-3 border-t pt-4">
                    <Button type="button" variant="outline" onClick={onClose} disabled={isBusy}>
                      閉じる
                    </Button>
                    {attribution.member_code && (
                      <Button type="button" onClick={() => void reissue()} disabled={isBusy}>
                        {isReissuing ? '発行中...' : '伝票QRを再発行'}
                      </Button>
                    )}
                  </div>
                </>
              )}
            </div>
          )
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * 見出しに出す顧客の呼び名。訂正の相手が誰か分からないまま無効化させないため、顧客未設定の受注も
 * 録入された連絡先で呼ぶ。見出しの行はすでに全体が注記の体裁なので、注記だけを分けて装飾しない。
 */
function customerText(order: Order | null): string {
  const label = order === null ? null : customerLabel(order);
  if (label === null) {
    return 'お客様名なし';
  }
  return label.unlinked ? `${label.name}${UNLINKED_NOTE}` : label.name;
}
