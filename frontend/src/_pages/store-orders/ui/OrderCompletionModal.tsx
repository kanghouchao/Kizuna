'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { Order, OrderCompletionPreview, orderApi } from '@/entities/order';
import { getApiErrorMessage, integerRule, useResource } from '@/shared/lib';
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

/** 空欄と 0 は別物なので、未入力の文言は 1 箇所に持って両方の規則から指す。 */
const TOTAL_FEE_REQUIRED = '会計金額を入力してください';

interface OrderCompletionFormValues {
  /** 空欄は NaN。「未入力」と 0 円の会計を取り違えないため、valueAsNumber の写像をそのまま持つ。 */
  total_fee: number;
  /** 空欄（NaN）は利用なし。 */
  use_points: number;
}

/**
 * 取得した見込みと、その取得元の受注。取得フックは取り直しの間も前の値を保つため、
 * 値だけでは別の受注の見込みと見分けられない。
 */
interface PreviewSnapshot {
  orderId: string;
  body: OrderCompletionPreview;
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
    defaultValues: { total_fee: NaN, use_points: NaN },
  });
  const {
    handleSubmit,
    reset,
    control,
    formState: { isSubmitting },
  } = form;

  const orderId = order?.id ?? '';
  // 見込みは打鍵ごとではなく、欄を離れた時点の金額で取り直す。確定値を「どの受注で確定したか」
  // ごと持つのは、別の受注へ切り替わったフレームで前の受注の金額の見込みを出さないため
  // （欄は空に戻っているので、金額だけ残ると付与予定が嘘になる）。
  const [committed, setCommitted] = useState<{ orderId: string; fee: number } | null>(null);
  const committedFee = committed !== null && committed.orderId === orderId ? committed.fee : 0;

  // 閉じている間は取りに行かない（開いた時点で取り直す）
  const {
    data: snapshot,
    isLoading: previewLoading,
    failure: previewFailure,
    reload: reloadPreview,
  } = useResource<PreviewSnapshot>(
    order === null
      ? null
      : async () => ({ orderId, body: await orderApi.completionPreview(orderId, committedFee) }),
    [orderId, committedFee]
  );
  // 別の受注へ切り替わった瞬間は見込みを持たない状態から始める（レンダー期の判定なので、前の受注の
  // 紐づけで欄の可否や送信可否を決めるフレームが 1 つも無い）。同じ受注で金額を取り直している間だけ
  // 前の値を出したままにする。
  const preview = snapshot !== null && snapshot.orderId === orderId ? snapshot.body : null;

  useEffect(() => {
    if (!order) return;
    reset({ total_fee: NaN, use_points: NaN });
    // 確定値も欄と一緒に戻す。同じ受注を開き直したとき、空の欄のまま前回の金額で付与予定が出る
    setCommitted(null);
  }, [order, reset]);

  const submit = async (values: OrderCompletionFormValues) => {
    if (!order?.id) return;
    // 欄が消えても react-hook-form は値を保つ。非会員の受注へ持ち越した利用を送らないよう、
    // 送信可否は入力ではなく今の見込みで決める。
    const usePoints = preview?.member_linked === true ? values.use_points : NaN;
    try {
      await orderApi.complete(order.id, {
        total_fee: values.total_fee,
        // 0 はサーバ側の @Min(1) に撥ねられる。利用しない完了では項目ごと送らない
        // （undefined は JSON 化の段でキーごと消える）。
        use_points: usePoints > 0 ? usePoints : undefined,
      });
      notify.success('オーダーを完了しました');
      onCompleted();
      onClose();
    } catch (error) {
      // 残高不足・単位違反・非会員の利用は、サーバが対処できる文言を返す。汎用文言に潰さない。
      notify.error(getApiErrorMessage(error, 'オーダーの完了に失敗しました'));
    }
  };

  return (
    <Dialog
      open={order !== null}
      onOpenChange={next => {
        // 送信中に閉じると、台帳へ記帳されたかどうか分からないまま古い一覧が残る
        if (!next && !isSubmitting) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <div className="border-b px-6 py-4">
          <DialogTitle>完了処理</DialogTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            {order?.business_date ?? '-'} / {order?.customer_name || 'お客様名なし'}
          </p>
        </div>
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。required と min={0} は下の規則が引き継ぐ */}
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
            <FormField
              control={control}
              name="total_fee"
              rules={{
                required: TOTAL_FEE_REQUIRED,
                min: { value: 0, message: '会計金額は 0 以上です' },
                validate: {
                  // 空欄は NaN であって null でも空文字でもないため required は素通りする
                  notEmpty: value => !Number.isNaN(value) || TOTAL_FEE_REQUIRED,
                  // noValidate は type="number" の暗黙の step=1 も止める。これが無いと
                  // 1.5 が Integer の totalFee へ届く
                  integer: integerRule('会計金額'),
                },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>会計金額</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      required
                      min={0}
                      {...field}
                      // register の valueAsNumber と同じ写像。Number() は空欄を 0 にしてしまい、
                      // 「未入力」を表す NaN が失われる。
                      value={Number.isNaN(field.value) ? '' : field.value}
                      onChange={event => field.onChange(event.target.valueAsNumber)}
                      onBlur={event => {
                        field.onBlur();
                        const fee = event.target.valueAsNumber;
                        setCommitted({ orderId, fee: Number.isNaN(fee) ? 0 : fee });
                      }}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
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
      </DialogContent>
    </Dialog>
  );
}
