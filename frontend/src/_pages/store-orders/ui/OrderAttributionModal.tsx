'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { Order, OrderAttribution, OrderAttributionSource, orderApi } from '@/entities/order';
import { getApiErrorMessage, hasPermission, readTokenClaims, useResource } from '@/shared/lib';
import { customerHeadingText } from '../lib/customerLabel';
import { AttributionCorrectionStep } from './AttributionCorrectionStep';
import { ReceiptTokenPanel } from './ReceiptTokenPanel';
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
const SOURCE_LABELS: Record<OrderAttributionSource, string> = {
  COMPLETION: '完了時の会員紐づけ',
  RECEIPT_TOKEN: '伝票QRの申領',
};

/**
 * 無効化の前に出す注意書き。
 *
 * 無効化そのものは台帳へ波及しない（ADR 0009）。誤って付与された分は続く段で差し引くので、
 * ここでは「無効化だけでは戻らない」ことと「この直後に差し引きへ進む」ことを述べる。
 */
const CORRECTION_NOTE =
  '無効化しても付与済みのポイントは自動では戻りません。無効化のあと、この帰属の会員から差し引く手続きへ進みます。';

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

/**
 * 差し引きの対象。宛先は<b>この帰属記録</b>が持つ会員で、受注から導き直さない — 開いたまま別の操作者が
 * 訂正を一巡させると、受注の直近の記録は正しい本人の新しい帰属に入れ替わっている。
 */
interface CorrectionTarget {
  orderId: string;
  attributionId: number;
  memberCode?: string;
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
 * 誤帰属の訂正モーダル（帰属記録の無効化、誤付与の差し引き、伝票QRの再発行）。
 *
 * 無効化は帰属記録に対する唯一の訂正操作で、行は削除されず理由・実行者・時刻が残る。
 * ポイント台帳へは波及しないため、誤って付与された分は続く段で<b>帰属記録が持つ会員</b>から
 * 差し引く（ADR 0012）。領域としては二段だが、画面は一段目の直後に二段目へ送る — 同じ操作者の
 * 同じ会話の中で閉じないと、やり残しに気づく機会がどこにも無いためである。
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
  // 差し引きの宛先に渡す ID。サーバが名指す「まだ差し引かれていない誤付与を持つ記録」で、現況の記録とは
  // 別でありうる。閉包の中で直に読むと省略可能なままで型が絞れないため、束縛を 1 つ挟む。
  const pendingCorrectionId = attribution?.pending_correction_attribution_id;

  const [reissued, setReissued] = useState<ReissuedReceiptToken | null>(null);
  const receiptToken = reissued !== null && reissued.orderId === orderId ? reissued.token : null;
  const [isReissuing, setIsReissuing] = useState(false);

  const [correcting, setCorrecting] = useState<CorrectionTarget | null>(null);
  const correctionTarget =
    correcting !== null && correcting.orderId === orderId ? correcting : null;
  // 差し引きの送信中は閉じない。子が持つ送信状態をここへ上げる（ESC・背景押下で閉じると、訂正が
  // 成立したか分からないまま冪等キーが失われ、開き直しでは別のキーになる）。
  const [isCorrectingBusy, setIsCorrectingBusy] = useState(false);

  // 権限による UI 出し分け（強制はサーバ側 @PreAuthorize — ここは導線の表示制御のみ）。
  // token claim の authorities から読む。token 無し・壊れは導線を出さない（fail-closed）。
  // レンダー期ではなく効果で読むのは、claim が描画前に読めない環境で出し分けが揺れないようにするため。
  const [canAdjust, setCanAdjust] = useState(false);
  useEffect(() => {
    setCanAdjust(hasPermission(readTokenClaims(), 'POINT_ADJUST'));
  }, []);

  useEffect(() => {
    if (!order) return;
    reset({ reason: '' });
    // 発行済みの QR も一緒に戻す。「今だけ表示できる」と書いた画面が、開き直しで前の QR を出し直さない
    setReissued(null);
    setCorrecting(null);
    setIsCorrectingBusy(false);
  }, [order, reset]);

  const invalidate = async (values: InvalidationFormValues) => {
    // 対象は画面が読み口で得た記録そのものを名指す。受注から導く形へ戻すと、開いたまま別の操作者が
    // 訂正を一巡させた場合に、この理由が新しく成立した正しい帰属へ当たって来店を消す
    if (!order?.id || attribution?.id === undefined) return;
    try {
      const updated = await orderApi.invalidateAttribution(order.id, {
        attribution_id: attribution.id,
        reason: values.reason,
      });
      notify.success('帰属を無効化しました');
      // 応答が訂正後の現況を持つので、取り直さず差し替える（読み込み表示で一瞬消えない）
      setSnapshot({ orderId, body: updated });
      reset({ reason: '' });
      // そのまま二段目へ送る。ここで通常の画面に戻すと、誤って付与された分が相手の台帳に残ったまま
      // 「訂正が済んだ」ことになり、やり残しに気づく機会がどこにも無くなる。宛先には今まさに倒した
      // 記録を渡す（受注から導き直さない）。
      setCorrecting({
        orderId,
        attributionId: attribution.id,
        memberCode: attribution.member_code,
      });
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

  const isBusy = isSubmitting || isReissuing || isCorrectingBusy;
  const dialogTitle =
    receiptToken !== null
      ? '伝票QRコード'
      : correctionTarget !== null
        ? '誤付与の差し引き'
        : '会員帰属の訂正';

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
          <DialogTitle>{dialogTitle}</DialogTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            {order?.business_date ?? '-'} / {customerHeadingText(order)}
          </p>
        </div>
        {receiptToken !== null ? (
          <ReceiptTokenPanel
            token={receiptToken}
            claimNote="正しいお客様が読み取ると、この来店をご自身のポイントと履歴に取り込めます（再発行から 90 日以内）。"
            onClose={onClose}
          />
        ) : correctionTarget !== null ? (
          <AttributionCorrectionStep
            orderId={correctionTarget.orderId}
            attributionId={correctionTarget.attributionId}
            memberCode={correctionTarget.memberCode}
            onBusyChange={setIsCorrectingBusy}
            onDone={() => {
              setCorrecting(null);
              // 差し引きが済むと残りの誤付与も変わる。取り直さないと、下の導線が済んだ訂正を
              // 指し続ける。
              void reload();
            }}
          />
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

              {/* 差し引きへ戻る口。無効化の直後に送る導線を閉じてしまうと、ここが唯一の入口になる。
                  宛先はサーバが名指す古い記録なので、正しい本人が申領を済ませて現況が入れ替わった後でも
                  同じ相手に届く（受注から導き直すと、その本人の来店を消す操作になってしまう） */}
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
                        orderId,
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
                // 無効化は POINT_ADJUST（店長）限定。押せない導線を描くと、理由を入力し終えてから
                // 拒否されることになる
                <>
                  <p className="text-sm text-muted-foreground">
                    この帰属を訂正するには、ポイント調整の権限が必要です。店長にご依頼ください。
                  </p>
                  <div className="flex justify-end border-t pt-4">
                    <Button type="button" variant="outline" onClick={onClose}>
                      閉じる
                    </Button>
                  </div>
                </>
              ) : attribution.attributed ? (
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
                    <p className="text-sm text-muted-foreground">{CORRECTION_NOTE}</p>
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
                      {/* 2 度目の再発行は前の QR を殺す。押した後に気づいても渡した QR は戻せないので、
                          取り消せない副作用として操作の手前で名乗る */}
                      前に発行した伝票QRは使えなくなります。
                    </p>
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      この受注は会員へ帰属したことがないため、訂正の対象ではありません。
                    </p>
                  )}
                  <div className="flex flex-wrap justify-end gap-3 border-t pt-4">
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
