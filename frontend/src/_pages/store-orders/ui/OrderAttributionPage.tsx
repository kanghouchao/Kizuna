'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { hasPermission, useCurrentStoreId, useMe, useStoreContext } from '@/entities/user';
import { notify } from '@/shared/notify';
import { Order, OrderAttribution, OrderAttributionSource, orderApi } from '@/entities/order';
import {
  getApiErrorMessage,
  storePath,
  useKeyedResource,
  useResourceInitialization,
} from '@/shared/lib';
import {
  Badge,
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  RegionError,
  Textarea,
} from '@/shared/ui';
import { customerHeadingText } from '../lib/customerLabel';
import { AttributionCorrectionStep } from './AttributionCorrectionStep';
import { ReceiptTokenDialog } from './ReceiptTokenDialog';

/** 理由の上限。帰属記録側の @Size(max = 500) と同値。 */
const REASON_MAX_LENGTH = 500;

/** 成立の機構の日本語表示。値そのものは事実の記録で、挙動は分けない。 */
const SOURCE_LABELS: Record<OrderAttributionSource, string> = {
  COMPLETION: '完了時の会員紐づけ',
  RECEIPT_TOKEN: '伝票QRの申領',
};

/** 無効化は台帳へ波及しない（ADR 0009）ので、誤付与はこの直後の差し引きで戻すと操作の前に述べる。 */
const CORRECTION_NOTE =
  '無効化しても付与済みのポイントは自動では戻りません。無効化のあと、この帰属の会員から差し引く手続きへ進みます。';

interface InvalidationFormValues {
  reason: string;
}

/**
 * 差し引きの対象。宛先は<b>この帰属記録</b>が持つ会員で、受注から導き直さない — 開いたまま別の操作者が
 * 訂正を一巡させると、受注の直近の記録は正しい本人の新しい帰属に入れ替わっている。
 */
interface CorrectionTarget {
  attributionId: number;
  memberCode?: string;
}

function formatDateTime(value?: string): string {
  return value ? new Date(value).toLocaleString('ja-JP') : '-';
}

export default function OrderAttributionPage() {
  const id = useParams().id as string;
  const storeId = useStoreContext().currentStoreId;
  return <Attribution key={`${storeId}:${id}`} id={id} />;
}

/**
 * 誤帰属の訂正（帰属記録の無効化、誤付与の差し引き、伝票QRの再発行）。
 *
 * 無効化は台帳へ波及しないので、誤付与は続けて<b>帰属記録が持つ会員</b>から差し引く（ADR 0012）。
 * 無効化の直後に差し引きの節へ送るのは、同じ流れで済ませないとやり残しに気づく機会が無いためである。
 */
function Attribution({ id }: { id: string }) {
  const router = useRouter();
  const storeId = useCurrentStoreId();
  const listHref = storePath(storeId, '/orders');
  const form = useForm<InvalidationFormValues>({ defaultValues: { reason: '' } });
  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;

  const orderResource = useKeyedResource<Order>(['order', storeId, id], () => orderApi.get(id));
  const { data: order, failure: orderFailure, reload: reloadOrder } = orderResource;
  const resource = useKeyedResource<OrderAttribution>(['attribution', storeId, id], () =>
    orderApi.attribution(id)
  );
  const { data: attribution, isLoading, failure, reload } = resource;
  const initialized = useResourceInitialization(resource.success, () => reset({ reason: '' }));
  // 差し引きの宛先に渡す ID。サーバが名指す「まだ差し引かれていない誤付与を持つ記録」で、現況の記録とは
  // 別でありうる。閉包の中で直に読むと省略可能なままで型が絞れないため、束縛を 1 つ挟む。
  const pendingCorrectionId = attribution?.pending_correction_attribution_id;

  // 再発行した伝票トークンの生値。応答にしか現れないので、メモリにだけ持つ
  const [receiptToken, setReceiptToken] = useState<string | null>(null);
  const [isReissuing, setIsReissuing] = useState(false);
  const [correcting, setCorrecting] = useState<CorrectionTarget | null>(null);
  // 差し引きの送信状態を子から上げる。送信中に離れると、成立したか分からないまま冪等キーが失われる
  const [isCorrectingBusy, setIsCorrectingBusy] = useState(false);

  // 権限による UI 出し分け（強制はサーバ側 @PreAuthorize — ここは導線の表示制御のみ）。
  // /me の取得前・失敗時は導線を出さない（fail-closed）。
  const canAdjust = hasPermission(useMe().me, 'POINT_ADJUST');

  const invalidate = async (values: InvalidationFormValues) => {
    // 対象は画面が読み口で得た記録そのものを名指す。受注から導く形へ戻すと、開いたまま別の操作者が
    // 訂正を一巡させた場合に、この理由が新しく成立した正しい帰属へ当たって来店を消す
    if (attribution?.id === undefined || !initialized || isLoading) return;
    const operation = resource.capture();
    try {
      const updated = await orderApi.invalidateAttribution(id, {
        attribution_id: attribution.id,
        reason: values.reason,
      });
      // 応答が訂正後の現況を持つので、取り直さず差し替える（読み込み表示で一瞬消えない）
      if (!operation.replace(updated)) return;
      notify.success('帰属を無効化しました');
      // 宛先には今まさに倒した記録を渡す（受注から導き直さない）
      setCorrecting({ attributionId: attribution.id, memberCode: attribution.member_code });
    } catch (error) {
      if (!operation.isCurrent()) return;
      // 帰属していない・既に無効化済みは、サーバが対処できる文言を返す。汎用文言に潰さない。
      notify.error(getApiErrorMessage(error, '帰属の無効化に失敗しました'));
    }
  };

  const reissue = async () => {
    if (order === null || order.completion_invalidated) return;
    const operation = resource.capture();
    try {
      setIsReissuing(true);
      const issued = await orderApi.reissueReceiptToken(id);
      if (!operation.isCurrent()) return;
      notify.success('伝票QRを再発行しました');
      setReceiptToken(issued.receipt_token);
    } catch (error) {
      if (!operation.isCurrent()) return;
      notify.error(getApiErrorMessage(error, '伝票QRの再発行に失敗しました'));
    } finally {
      if (operation.isCurrent()) setIsReissuing(false);
    }
  };

  const isBusy = isSubmitting || isReissuing || isCorrectingBusy;
  const fallback = { href: listHref, label: 'オーダー一覧へ' };
  const missing = orderFailure === 'notFound' || failure === 'notFound';
  const notCompleted = order !== null && order.status !== 'COMPLETED';
  const heading = `${order?.business_date ?? '-'} / ${customerHeadingText(order)}`;
  const ready =
    order !== null && !isLoading && failure === null && initialized && attribution !== null;
  const showsInvalidationForm = ready && canAdjust && attribution.attributed;
  const canReissue =
    ready && !attribution.attributed && !!attribution.member_code && !order.completion_invalidated;

  const backButton = isBusy ? (
    // 送信中に離れると、応答にしか現れない伝票トークンや差し引きの成否を受け取れずに失う
    <Button type="button" variant="outline" disabled>
      戻る
    </Button>
  ) : (
    <Button variant="outline" render={<Link href={listHref} />}>
      戻る
    </Button>
  );

  return (
    <>
      <ReceiptTokenDialog
        token={receiptToken}
        description={heading}
        claimNote="正しいお客様が読み取ると、この来店をご自身のポイントと履歴に取り込めます（再発行から 90 日以内）。"
        onClose={() => router.replace(listHref)}
      />
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">会員帰属の訂正</h1>
          {order !== null && <p className="mt-1 text-sm text-muted-foreground">{heading}</p>}
        </div>
        {orderFailure === 'error' ? (
          <RegionError message="受注を取得できませんでした。" onRetry={() => void reloadOrder()} />
        ) : missing ? (
          <RegionError message="受注が見つかりません。" fallback={fallback} />
        ) : notCompleted ? (
          // 帰属が生まれるのは完了と事後申領の瞬間だけで、未完了・取消済みの受注には訂正の対象が無い
          <RegionError message="完了した受注だけが会員帰属を訂正できます。" fallback={fallback} />
        ) : failure !== null ? (
          // 読めなかった現況を「未帰属」で描くと、他人の来店が残っているのに再発行を勧めることになる
          <RegionError message="帰属の状況を取得できませんでした" onRetry={() => void reload()} />
        ) : !ready ? (
          <p className="text-sm text-muted-foreground">読み込み中...</p>
        ) : correcting !== null ? (
          <section className="space-y-4 rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
            <h2 className="font-semibold text-foreground">誤付与の差し引き</h2>
            <AttributionCorrectionStep
              orderId={id}
              attributionId={correcting.attributionId}
              memberCode={correcting.memberCode}
              onBusyChange={setIsCorrectingBusy}
              onDone={() => {
                setCorrecting(null);
                // 差し引きが済むと残りの誤付与も変わる。取り直さないと、差し引きへの導線が済んだ訂正を指し続ける
                void reload();
              }}
            />
          </section>
        ) : (
          <Form {...form}>
            {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
                我々の文言は永久に描かれない。required は下の規則が引き継ぐ */}
            <form
              onSubmit={showsInvalidationForm ? handleSubmit(invalidate) : e => e.preventDefault()}
              noValidate
              className="space-y-4 rounded-xl border bg-card p-6 text-card-foreground shadow-sm"
            >
              <h2 className="font-semibold text-foreground">現在の帰属</h2>
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
                      {attribution.source ? SOURCE_LABELS[attribution.source] : '-'} /{' '}
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

              {/* 差し引きへ戻る口。後回しにした差し引きはここが唯一の入口になる。宛先はサーバが名指す古い記録
                  なので、正しい本人が申領を済ませて現況が入れ替わった後でも同じ相手に届く */}
              {canAdjust && pendingCorrectionId !== undefined && (
                <div className="space-y-3 rounded-md border p-3">
                  <p className="text-sm text-foreground">
                    {attribution.pending_correction_member_code ?? '無効化した会員'}
                    に、誤って付与されたまま差し引かれていないポイントが残っています。
                  </p>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={isBusy}
                    onClick={() =>
                      setCorrecting({
                        attributionId: pendingCorrectionId,
                        memberCode: attribution.pending_correction_member_code,
                      })
                    }
                  >
                    ポイントを差し引く
                  </Button>
                </div>
              )}

              {!canAdjust && attribution.attributed ? (
                // 無効化は POINT_ADJUST（店長）限定。押せない導線を描くと、理由を入力し終えてから拒否される
                <p className="text-sm text-muted-foreground">
                  この帰属を訂正するには、ポイント調整の権限が必要です。店長にご依頼ください。
                </p>
              ) : attribution.attributed ? (
                <>
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
                  <p className="text-sm text-muted-foreground">{CORRECTION_NOTE}</p>
                </>
              ) : order.completion_invalidated ? (
                <p className="text-sm text-muted-foreground">
                  無効化した受注に伝票は発行できません。
                </p>
              ) : attribution.member_code ? (
                <p className="text-sm text-muted-foreground">
                  伝票QRを再発行すると、正しいお客様が読み取ってこの来店を取り戻せます。
                  {/* 2 度目の再発行は前の QR を殺す。渡した QR は戻せないので、取り消せない副作用として手前で名乗る */}
                  前に発行した伝票QRは使えなくなります。
                </p>
              ) : (
                <p className="text-sm text-muted-foreground">
                  この受注は会員へ帰属したことがないため、訂正の対象ではありません。
                </p>
              )}

              <div className="flex justify-end gap-3">
                {backButton}
                {showsInvalidationForm && (
                  <Button type="submit" disabled={isBusy}>
                    {isSubmitting ? '処理中...' : '無効化する'}
                  </Button>
                )}
                {canReissue && (
                  <Button type="button" onClick={() => void reissue()} disabled={isBusy}>
                    {isReissuing ? '発行中...' : '伝票QRを再発行'}
                  </Button>
                )}
              </div>
            </form>
          </Form>
        )}
        {/* 一覧への導線を持たない表示（差し引きの節・読み込み中・再試行）にだけ添える */}
        {(correcting !== null || (!ready && !missing && !notCompleted)) && <div>{backButton}</div>}
      </div>
    </>
  );
}
