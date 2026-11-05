'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useForm, useWatch } from 'react-hook-form';
import { useStoreContext } from '@/entities/user';
import { notify } from '@/shared/notify';
import {
  Order,
  OrderPreview,
  OrderFeeLineInput,
  feeLinesTotal,
  orderApi,
  storeEditableFeeLines,
  readOnlyFeeLines,
  toFeeLineInputs,
} from '@/entities/order';
import {
  getApiErrorMessage,
  integerRule,
  isConflict,
  useKeyedResource,
  useResourceInitialization,
} from '@/shared/lib';
import {
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  KeyValueList,
  KeyValueRow,
  RegionError,
} from '@/shared/ui';
import { customerHeadingText } from '../lib/customerLabel';
import { orderListHref } from '../lib/orderListHref';
import { OrderEditorSection } from './OrderEditorSection';
import { OrderFeeLinesField } from './OrderFeeLinesField';
import { ReceiptTokenDialog } from './ReceiptTokenDialog';
import { orderConflictField, useOrderConfirmation } from './useOrderConfirmation';

interface OrderCompletionFormValues {
  /** 会計の内訳。合計はこの総和としてサーバが導出するので、画面に会計金額の欄は無い。 */
  fee_lines: OrderFeeLineInput[];
  /** 空欄（NaN）は利用なし。 */
  use_points: number;
}

const isTerminal = (order: Order) => order.status === 'COMPLETED' || order.status === 'CANCELLED';

export default function OrderCompletionPage() {
  const id = useParams().id as string;
  const storeId = useStoreContext().currentStoreId;
  return <Completion key={`${storeId}:${id}`} id={id} />;
}

/**
 * 受注の完了（会計）。
 *
 * ポイントの利用と自動付与が台帳へ入る経路はこの操作だけなので、会計金額の確定と
 * ポイントの利用はここで同時に決める。付与見込み・利用単位・残高は自前で計算せず
 * 事前計算の読み口から引く — 画面が独自に計算すると、設定変更のたびに見せた見込みと
 * 確定の結果が食い違う。手元の規則は入力をその場で直せるようにするためのもので、最終的な権威はサーバ側にある。
 */
function Completion({ id }: { id: string }) {
  const router = useRouter();
  const storeId = useStoreContext().currentStoreId;
  // 一覧から渡された検索条件。戻る・完了後の遷移で同じ条件の作業キューへ帰る
  const backHref = orderListHref(useSearchParams().toString());
  const confirmation = useOrderConfirmation(id);
  const form = useForm<OrderCompletionFormValues>({
    defaultValues: { fee_lines: [], use_points: NaN },
  });
  const {
    handleSubmit,
    reset,
    control,
    formState: { isSubmitting },
  } = form;

  // 見込みは打鍵ごとではなく、欄を離れた時点の金額で取り直す。合計はサーバが行から導出するので、
  // ここで足すのは見込みの取得と利用ポイントの上限判定に要る「ポイント利用が入る前の総和」だけ。
  const [committedFee, setCommittedFee] = useState(0);

  const resource = useKeyedResource<Order>(['order', storeId, id], () => orderApi.get(id));
  const { data: detail, failure: detailFailure, reload: reloadDetail } = resource;
  const usePoints = useWatch({ control, name: 'use_points' });
  const chargeAmount =
    feeLinesTotal(useWatch({ control, name: 'fee_lines' }) ?? []) +
    (detail?.course.price ?? 0) +
    (detail?.special_services ?? []).reduce((sum, item) => sum + item.price, 0);
  const initialized = useResourceInitialization(resource.success, detail => {
    if (isTerminal(detail)) return;
    const existing = storeEditableFeeLines(detail.fee_lines);
    reset({ fee_lines: existing, use_points: NaN });
    setCommittedFee(feeLinesTotal(existing));
  });
  const terminal = detail !== null && isTerminal(detail);
  const seeded = initialized && !resource.isLoading && detail !== null && !terminal;
  const {
    data: previewResult,
    isLoading: previewLoading,
    failure: previewFailure,
    reload: reloadPreview,
  } = useKeyedResource<OrderPreview>(
    ['completionPreview', storeId, id],
    !seeded
      ? null
      : () =>
          orderApi.completionPreview(id, {
            expected_version: detail!.version!,
            fee_lines: toFeeLineInputs(form.getValues('fee_lines')),
          }),
    [committedFee]
  );
  const preview = previewResult?.points ?? null;

  // 会員へ帰属しなかった完了でだけ返る伝票トークン。生値はこの応答にしか現れないので、メモリにだけ持つ
  const [receiptToken, setReceiptToken] = useState<string | null>(null);

  const submit = async (values: OrderCompletionFormValues) => {
    // 版は完了の必須項目。詳細が無い／版を運んでいない姿では送る先が決まらないので、送らない
    if (!seeded || detail === null || detail.version === undefined) return;
    const operation = resource.capture();
    try {
      const request = {
        // 版は内訳を播いた詳細そのものから採る。別の取得元から採ると、送る内訳と名指す版が
        // 別の瞬間の姿になり、照合が通ったのに古い内訳で凍らせる完了が成立する
        expected_version: detail.version,
        fee_lines: toFeeLineInputs(values.fee_lines),
        // 0 はサーバ側の @Min(1) に撥ねられる。利用しない完了では項目ごと送らない
        use_points: values.use_points > 0 ? values.use_points : undefined,
      };
      const token = await confirmation.confirm(() => orderApi.completionPreview(id, request));
      if (!token) return;
      const completed = await orderApi.complete(id, { ...request, confirmation_token: token });
      if (!operation.isCurrent()) return;
      notify.success('オーダーを完了しました');
      if (completed.receipt_token) setReceiptToken(completed.receipt_token);
      // 履歴を置き換え、戻るで完了済みの受注の画面へ帰らないようにする
      else router.replace(backHref);
    } catch (error) {
      if (!operation.isCurrent()) return;
      if (orderConflictField(error) === 'customer_lock') {
        notify.error(
          getApiErrorMessage(error, '顧客情報が変更中です。しばらく待ってから再試算してください')
        );
        return;
      }
      if (orderConflictField(error) === 'use_points') {
        notify.error(
          `${getApiErrorMessage(error, 'ポイントの利用条件が変更されています')}。利用点数を見直してください`
        );
        return;
      }
      if (orderConflictField(error) === 'confirmation_token') {
        notify.warning(
          '採用条件が変更されています。入力を保持しました。再試算して確認してください'
        );
        return;
      }
      if (isConflict(error)) {
        // 取り直した受注が終端なら、播き直さずに画面が終端を名乗る（警告は重ねない）
        const result = await reloadDetail();
        if (result.status === 'success' && result.isCurrent() && !isTerminal(result.data)) {
          notify.warning('他の操作者が更新しました。入力を最新の内容に置き換えました');
        }
        return;
      }
      // 残高不足・単位違反・非会員の利用は、サーバが対処できる文言を返す。汎用文言に潰さない。
      notify.error(getApiErrorMessage(error, 'オーダーの完了に失敗しました'));
    }
  };

  const fallback = { href: backHref, label: 'オーダー一覧へ' };
  const heading = `${detail?.business_date ?? '-'} / ${customerHeadingText(detail)}`;
  return (
    <>
      {confirmation.dialog}
      <ReceiptTokenDialog
        token={receiptToken}
        description={heading}
        claimNote="お客様が読み取ると、この来店をご自身のポイントと履歴に取り込めます（完了から 90 日以内）。"
        onClose={() => router.replace(backHref)}
      />
      <div>
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-foreground">受注の完了</h1>
          {detail !== null && <p className="mt-1 text-sm text-muted-foreground">{heading}</p>}
        </div>
        {detailFailure === 'error' ? (
          <RegionError message="受注を取得できませんでした。" onRetry={() => void reloadDetail()} />
        ) : detailFailure === 'notFound' ? (
          <RegionError message="受注が見つかりません。" fallback={fallback} />
        ) : terminal ? (
          <RegionError
            message="この受注は完了または取消済みのため、完了できません。"
            fallback={fallback}
          />
        ) : !seeded ? (
          // 播く前はフォームを出さない。空欄のフォームを 1 フレームでも見せると、そのまま送って
          // 既存の内訳を消せる
          <p className="text-sm text-muted-foreground">読み込み中...</p>
        ) : (
          <Form {...form}>
            {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
            我々の文言は永久に描かれない。required と min={0} は下の規則が引き継ぐ */}
            <form
              onSubmit={handleSubmit(submit)}
              className="rounded-xl border bg-card text-card-foreground shadow-sm"
              noValidate
              // 行が増減しても取り直しの引き金が 1 つで済むよう、個々の欄ではなくフォームの focusout で拾う
              onBlur={() => setCommittedFee(chargeAmount)}
            >
              <OrderEditorSection
                title="会計"
                description="合計は内訳の総和です。コースと特別サービスは受注の内容から採ります。"
              >
                <OrderFeeLinesField systemLines={readOnlyFeeLines(detail?.fee_lines)} />
              </OrderEditorSection>
              <OrderEditorSection
                title="ポイント"
                description="付与と利用は完了したときに台帳へ記帳されます。見込みは内訳の欄を離れたときに取り直します。"
              >
                <section aria-label="ポイント" className="grid gap-6 md:grid-cols-2">
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
                      <KeyValueList>
                        <KeyValueRow label="会員">
                          {preview.member_linked ? '紐づけ済み' : '未紐づけ'}
                        </KeyValueRow>
                        {preview.point_balance !== undefined && (
                          <KeyValueRow label="残高" money>
                            {preview.point_balance} ポイント
                          </KeyValueRow>
                        )}
                        {/* 非会員の受注には付与も利用も無い。予定を出すと、完了しても増えないポイントを約束することになる */}
                        {preview.member_linked ? (
                          <>
                            <KeyValueRow label="付与予定" money>
                              {preview.grant_points} ポイント
                            </KeyValueRow>
                            <KeyValueRow label="利用単位" money>
                              {preview.usage_unit} ポイント単位
                            </KeyValueRow>
                          </>
                        ) : (
                          <KeyValueRow label="付与・利用" long>
                            非会員の受注には付与も利用もありません。
                          </KeyValueRow>
                        )}
                      </KeyValueList>
                    )
                  )}
                  {/* 資格が変わっても入力済みの利用値は消さず、利用者が明示的に修正できるよう残す。 */}
                  {(preview?.member_linked || usePoints > 0) && (
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
                            !preview?.member_linked ||
                            value % preview.usage_unit === 0 ||
                            `利用ポイントは ${preview?.usage_unit} ポイント単位で指定してください`,
                          withinBalance: value =>
                            Number.isNaN(value) ||
                            preview?.point_balance === undefined ||
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
                </section>
              </OrderEditorSection>
              <div className="flex items-center justify-between gap-6 rounded-b-xl bg-card p-6">
                <p className="text-sm text-muted-foreground">
                  完了前に料金・報酬・ポイントを確認できます。
                </p>
                <div className="flex shrink-0 gap-3">
                  {/* 送信中に離れると、応答にしか現れない伝票トークンを受け取れずに失う */}
                  {isSubmitting ? (
                    <Button type="button" variant="outline" disabled>
                      戻る
                    </Button>
                  ) : (
                    <Button variant="outline" render={<Link href={backHref} />}>
                      戻る
                    </Button>
                  )}
                  <Button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? '処理中...' : '完了する'}
                  </Button>
                </div>
              </div>
            </form>
          </Form>
        )}
      </div>
    </>
  );
}
