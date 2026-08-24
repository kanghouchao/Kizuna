'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  Order,
  OrderCompletionPreview,
  OrderFeeLineInput,
  feeLinesTotal,
  orderApi,
  storeEditableFeeLines,
  systemOwnedFeeLines,
  toFeeLineInputs,
} from '@/entities/order';
import { getApiErrorMessage, integerRule, isConflict, useResource } from '@/shared/lib';
import { customerHeadingText } from '../lib/customerLabel';
import { OrderFeeLinesField } from './OrderFeeLinesField';
import { ReceiptTokenPanel } from './ReceiptTokenPanel';
import {
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
  Input,
  RegionError,
} from '@/shared/ui';

interface OrderCompletionFormValues {
  /** 適用されたコース名の写し。会計の場が快照の最後の更新機会になる。 */
  course_name: string;
  /** 会計の内訳。合計はこの総和としてサーバが導出するので、画面に会計金額の欄は無い。 */
  fee_lines: OrderFeeLineInput[];
  /** 空欄（NaN）は利用なし。 */
  use_points: number;
}

/** 内訳を持たない受注を開いたときの初期行。打ち始められる空の 1 行を出す。 */
const EMPTY_FEE_LINE: OrderFeeLineInput = { kind: 'OPTION', name: '', amount: NaN };

/**
 * 取得した見込みと、その取得元の受注。取得フックは取り直しの間も前の値を保つため、
 * 値だけでは別の受注の見込みと見分けられない。
 */
interface PreviewSnapshot {
  orderId: string;
  body: OrderCompletionPreview;
}

/** 発行された伝票トークンと、その発行元の受注（見込みと同じ理由で、値だけでは別の受注のものと見分けられない）。 */
interface IssuedReceiptToken {
  orderId: string;
  token: string;
}

interface OrderCompletionModalProps {
  /** 完了処理の対象。null なら閉じている。 */
  order: Order | null;
  onClose: () => void;
  /** 完了の成功後に呼ばれる（受注の状態と会計欄が変わるため、一覧の取り直しに使う）。 */
  onCompleted: () => void;
}

/**
 * 受注の完了（会計）モーダル。
 *
 * ポイントの利用と自動付与が台帳へ入る経路はこの操作だけなので、会計金額の確定と
 * ポイントの利用はここで同時に決める。付与見込み・利用単位・残高は自前で計算せず
 * 事前計算の読み口から引く — 画面が独自に計算すると、設定変更のたびに見せた見込みと
 * 確定の結果が食い違う。
 *
 * 手元の規則は入力をその場で直せるようにするためのもので、単位・残高・会員資格の
 * 最終的な権威はサーバ側にある。
 */
export function OrderCompletionModal({ order, onClose, onCompleted }: OrderCompletionModalProps) {
  const form = useForm<OrderCompletionFormValues>({
    defaultValues: { course_name: '', fee_lines: [EMPTY_FEE_LINE], use_points: NaN },
  });
  const {
    handleSubmit,
    reset,
    control,
    watch,
    formState: { isSubmitting },
  } = form;

  const orderId = order?.id ?? '';
  // 合計はサーバが行から導出する。ここで足すのは、見込みの取得と利用ポイントの上限判定に要る
  // 「ポイント利用が入る前の総和」を手元で持つためだけ。
  const chargeAmount = feeLinesTotal(watch('fee_lines') ?? []);
  const courseName = watch('course_name');
  // 見込みは打鍵ごとではなく、欄を離れた時点の金額で取り直す。確定値を「どの受注で確定したか」
  // ごと持つのは、別の受注へ切り替わったフレームで前の受注の金額の見込みを出さないため
  // （欄は空に戻っているので、金額だけ残ると付与予定が嘘になる）。
  const [committed, setCommitted] = useState<{ orderId: string; fee: number } | null>(null);
  const committedFee = committed !== null && committed.orderId === orderId ? committed.fee : 0;

  // 播種は行ではなく詳細の読み口から取る。この面は作業キューの行から開かれ、行は明細もコース名も
  // 持たない（一覧と詳細の DTO は分かれている）。行だけで播くと、内訳のある受注が空行で開き、
  // そのまま完了すると既存の内訳を丸ごと上書きして失う。
  const {
    data: detail,
    failure: detailFailure,
    reload: reloadDetail,
  } = useResource<Order>(order === null ? null : () => orderApi.get(order.id), [orderId]);
  // 播き終えた受注。フォームを出す条件をこれにするのは、取得の到着がレンダーより後で、
  // 「取れた」で出すと播く前の 1 フレームが空欄のまま描かれるため（DESIGN.md）。
  const seededOrder = detail !== null && detail.id === orderId;
  const [seededId, setSeededId] = useState<string | null>(null);
  const seeded = seededOrder && seededId === orderId;

  // 閉じている間は取りに行かない（開いた時点で取り直す）。播く前に取りに行かないのは、
  // 内訳が入る前の総和 0 で見込みを引くと、明細のある受注の付与予定が最初の 1 画面だけ 0 で出るため。
  const {
    data: snapshot,
    isLoading: previewLoading,
    failure: previewFailure,
    reload: reloadPreview,
  } = useResource<PreviewSnapshot>(
    order === null || !seeded
      ? null
      : async () => ({ orderId, body: await orderApi.completionPreview(order.id, committedFee) }),
    [orderId, committedFee, seeded]
  );
  // 別の受注へ切り替わった瞬間は見込みを持たない状態から始める（レンダー期の判定なので、前の受注の
  // 紐づけで欄の可否や送信可否を決めるフレームが 1 つも無い）。同じ受注で金額を取り直している間だけ
  // 前の値を出したままにする。
  const preview = snapshot !== null && snapshot.orderId === orderId ? snapshot.body : null;

  // 発行された伝票トークン。会員へ帰属しなかった完了でだけ返るので、これが在る間は QR を出したまま
  // 閉じずに待つ（生値はこの応答にしか現れず、閉じると二度と出せない）。
  const [issued, setIssued] = useState<IssuedReceiptToken | null>(null);
  const receiptToken = issued !== null && issued.orderId === orderId ? issued.token : null;

  // 開き直したら播種をやり直す。useResource は取りに行かない間も持っている値を残すので、印を消さないと
  // 取り直しの完了を待たずに前の受注の内訳が出る。
  //
  // 発行済みの QR もここで戻す。播種の効果に委ねると、取り直しが同じ値を返して効果が走らなかった
  // ときに「今だけ表示できる」と書いた画面が前の QR を出し直す。
  useEffect(() => {
    setSeededId(null);
    setIssued(null);
  }, [orderId]);

  // 取得できたら播く。取得の到着はレンダーより後なので、初期値ではなく効果で入れる。
  //
  // 播き直しの引き金は<b>取得値そのものの到着</b>だけにする。useResource は取り直しの間も前の値を
  // 持ったままなので、印の有無で播くと開き直した瞬間に陳腐化した値で播いて印が立ち、後から着いた
  // 新しい内容が捨てられる — その内訳のまま完了すると、他の操作者が直した明細を古い値で上書きする。
  // 取り直し中は前と同じ値のまま効果が走らず、印を消した状態が続くのでフォームは出ない。
  useEffect(() => {
    if (detail === null) return;
    const existing = storeEditableFeeLines(detail.fee_lines);
    reset({
      course_name: detail.course_name ?? '',
      fee_lines: existing.length > 0 ? existing : [EMPTY_FEE_LINE],
      use_points: NaN,
    });
    // 見込みの基準も播いた内訳の総和から始める。0 のままだと、明細のある受注の付与予定が
    // 欄を離れるまで嘘になる
    setCommitted({ orderId: detail.id ?? '', fee: feeLinesTotal(existing) });
    setSeededId(detail.id ?? null);
  }, [detail, reset]);

  const submit = async (values: OrderCompletionFormValues) => {
    // 版は完了の必須項目。詳細が無い／版を運んでいない姿では送る先が決まらないので、送らない
    if (!order || detail === null || detail.version === undefined) return;
    // 欄が消えても react-hook-form は値を保つ。非会員の受注へ持ち越した利用を送らないよう、
    // 送信可否は入力ではなく今の見込みで決める。
    const usePoints = preview?.member_linked === true ? values.use_points : NaN;
    try {
      const completed = await orderApi.complete(order.id, {
        // 版は内訳を播いた詳細そのものから採る。別の取得元から採ると、送る内訳と名指す版が
        // 別の瞬間の姿になり、照合が通ったのに古い内訳で凍らせる完了が成立する
        expected_version: detail.version,
        // 空欄はそのまま空文字で送る。undefined はキーごと落ちてサーバが「変更しない」と読むため、
        // 消したい意図が黙って捨てられる（他の文字列項目と同じ作法）。
        course_name: values.course_name.trim(),
        fee_lines: toFeeLineInputs(values.fee_lines),
        // 0 はサーバ側の @Min(1) に撥ねられる。利用しない完了では項目ごと送らない
        // （undefined は JSON 化の段でキーごと消える）。
        use_points: usePoints > 0 ? usePoints : undefined,
      });
      notify.success('オーダーを完了しました');
      onCompleted();
      // 会員へ帰属した完了はトークンを持たないので、これまでどおり閉じる。
      if (completed.receipt_token) {
        setIssued({ orderId, token: completed.receipt_token });
      } else {
        onClose();
      }
    } catch (error) {
      // 残高不足・単位違反・非会員の利用は、サーバが対処できる文言を返す。汎用文言に潰さない。
      notify.error(getApiErrorMessage(error, 'オーダーの完了に失敗しました'));
      if (isConflict(error)) {
        // 版の食い違い。取り直さないと画面は古い版を持ったままで、その場の再送は何度でも 409 になる。
        // 取り直せば播種の効果が最新の内訳と版でフォームを組み直す（打ちかけの入力は破棄される）
        await reloadDetail();
      }
    }
  };

  return (
    <Dialog
      open={order !== null}
      onOpenChange={next => {
        // 送信中に閉じると、台帳へ記帳されたかどうか分からないまま古い一覧が残る。
        // QR を出している間も同じく閉じない — 生値はこの応答にしか無く、ESC や背景押下で
        // 誤って閉じると客が来店を取り戻す手段ごと消える。閉じるのは明示のボタンだけにする。
        if (!next && !isSubmitting && receiptToken === null) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <div className="border-b px-6 py-4">
          <DialogTitle>{receiptToken !== null ? '伝票QRコード' : '完了処理'}</DialogTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            {order?.business_date ?? '-'} / {customerHeadingText(order)}
          </p>
        </div>
        {receiptToken !== null ? (
          <ReceiptTokenPanel
            token={receiptToken}
            claimNote="お客様が読み取ると、この来店をご自身のポイントと履歴に取り込めます（完了から 90 日以内）。"
            onClose={onClose}
          />
        ) : !seeded ? (
          // 播く前はフォームを出さない。空欄のフォームを 1 フレームでも見せると、そのまま送って
          // 既存の内訳を消せる。失敗は領域が自分で名乗る（畳むと「読み込み中」のまま固まる）。
          <div className="px-6 py-5">
            {detailFailure === 'error' ? (
              <RegionError
                message="受注を取得できませんでした。"
                onRetry={() => void reloadDetail()}
              />
            ) : detailFailure === 'notFound' ? (
              // 404 は何度押しても取れないので再試行を出さない。背後の一覧が行き先なので出口は閉じること
              <div role="alert" className="flex items-center gap-3">
                <p className="text-sm text-destructive-strong">この受注は見つかりませんでした。</p>
                <Button type="button" variant="outline" size="sm" onClick={onClose}>
                  閉じる
                </Button>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">読み込み中...</p>
            )}
          </div>
        ) : (
          <Form {...form}>
            {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。required と min={0} は下の規則が引き継ぐ */}
            <form
              onSubmit={handleSubmit(submit)}
              className="space-y-4 px-6 py-5"
              noValidate
              // 見込みは打鍵ごとではなく、欄を離れた時点の内訳で取り直す。行が増減しても
              // 取り直しの引き金が 1 つで済むよう、個々の欄ではなくフォームの focusout で拾う。
              onBlur={() => setCommitted({ orderId, fee: chargeAmount })}
            >
              <FormField
                control={control}
                name="course_name"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>コース名</FormLabel>
                    <FormControl>
                      <Input {...field} maxLength={255} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <OrderFeeLinesField
                systemLines={systemOwnedFeeLines(order?.fee_lines)}
                courseName={courseName}
              />
              {/* 見込みが読めなくても送信は塞がない。単位も残高も会員資格もサーバ側が再検証する */}
              {previewLoading ? (
                <p className="text-sm text-muted-foreground">読み込み中...</p>
              ) : previewFailure !== null ? (
                <RegionError
                  message="ポイントの見込みを取得できませんでした"
                  onRetry={() => void reloadPreview()}
                />
              ) : (
                preview !== null && (
                  <div className="space-y-1 text-sm text-foreground">
                    <p>{preview.member_linked ? '会員紐づけ済み' : '未紐づけ'}</p>
                    {preview.point_balance !== undefined && (
                      <p>残高: {preview.point_balance} ポイント</p>
                    )}
                    {/* 非会員の受注には付与も利用も無い。予定を出すと、完了しても増えないポイントを約束することになる */}
                    {preview.member_linked && (
                      <>
                        <p>付与予定: {preview.grant_points} ポイント</p>
                        <p className="text-muted-foreground">
                          利用は {preview.usage_unit} ポイント単位で指定できます
                        </p>
                      </>
                    )}
                  </div>
                )
              )}
              {/* 非会員の受注にはポイントそのものが存在しないので、欄を出さない。同じ受注で金額を
                取り直している最中は直前の見込みのまま出したままにする（消えると打ちかけの値が
                視界から外れる） */}
              {preview !== null && preview.member_linked && (
                <FormField
                  control={control}
                  name="use_points"
                  rules={{
                    min: { value: 0, message: '利用ポイントは 0 以上です' },
                    validate: {
                      integer: integerRule('利用ポイント'),
                      // 空欄（NaN）は「利用なし」であって違反ではない。どの規則も素通りさせる
                      unit: value =>
                        Number.isNaN(value) ||
                        value % preview.usage_unit === 0 ||
                        `利用ポイントは ${preview.usage_unit} ポイント単位で指定してください`,
                      withinBalance: value =>
                        Number.isNaN(value) ||
                        preview.point_balance === undefined ||
                        value <= preview.point_balance ||
                        `残高を超えています（残高: ${preview.point_balance}）`,
                      // 請求より大きい割引に相当する利用は台帳へ積ませない。同額までは全額のポイント払い。
                      // 上限は「ポイント利用が入る前の総和」で、サーバの判定と同じ基準を使う
                      withinTotalFee: value =>
                        Number.isNaN(value) ||
                        value <= chargeAmount ||
                        `会計金額を超えています（会計金額: ${chargeAmount}）`,
                    },
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>利用ポイント</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          min={0}
                          {...field}
                          value={Number.isNaN(field.value) ? '' : field.value}
                          onChange={event => field.onChange(event.target.valueAsNumber)}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}
              <div className="flex justify-end gap-3 border-t pt-4">
                <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                  キャンセル
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? '処理中...' : '完了する'}
                </Button>
              </div>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  );
}
