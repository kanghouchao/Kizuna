'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  Order,
  OrderCorrectionResult,
  OrderFeeLineInput,
  orderApi,
  storeEditableFeeLines,
  systemOwnedFeeLines,
  toFeeLineInputs,
} from '@/entities/order';
import { ExternalLinkIcon } from 'lucide-react';
import { getApiErrorMessage, storePath, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { customerHeadingText } from '../lib/customerLabel';
// 空欄は「値なし」として送る。この契約は全量送信なので、省略と空欄が同じ意味になる。
import { optionalNumber, optionalTime, toTimeInput } from '../lib/formValues';
import { OrderFeeLinesField } from './OrderFeeLinesField';
import {
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
  Textarea,
} from '@/shared/ui';

interface OrderCorrectionFormValues {
  actual_arrival_time: string;
  actual_end_time: string;
  course_name: string;
  course_minutes: string;
  extension_minutes: string;
  fee_lines: OrderFeeLineInput[];
  reason: string;
}

const EMPTY_VALUES: OrderCorrectionFormValues = {
  actual_arrival_time: '',
  actual_end_time: '',
  course_name: '',
  course_minutes: '',
  extension_minutes: '',
  fee_lines: [],
  reason: '',
};

/** 訂正の結果と、その受注が現に帰属している会員（帰属していなければ null）。 */
interface CorrectionOutcomeState {
  result: OrderCorrectionResult;
  memberCode: string | null;
}

/**
 * 訂正の結果。動いたのは会計金額だけであることを示し、付与が動かないことを名乗る。
 *
 * 付与の差額も手当ての導線も出さない。手当ては別機構（手動調整）が担い、その調整は受注にも帰属記録にも
 * 結び付かないため、門は「前回の助言が実行されたか」を知れない — 可執行の額として出すと、二度目の訂正が
 * 一度目の手当てを勘定に入れないまま次の額を勧める。名乗るのは「どの会員の台帳を見ればよいか」までとし、
 * 要否と額の判断は台帳側の画面に委ねる。
 */
function CorrectionOutcome({ outcome }: { outcome: CorrectionOutcomeState }) {
  const { result, memberCode } = outcome;
  return (
    <div className="bg-card space-y-3 rounded-xl border p-4">
      <h2 className="text-foreground text-sm font-medium">訂正しました</h2>
      <p className="text-muted-foreground text-sm">
        請求 ¥{(result.previous_total_fee ?? 0).toLocaleString()} → ¥
        {(result.total_fee ?? 0).toLocaleString()}
      </p>
      {memberCode === null ? (
        <p className="text-muted-foreground text-sm">
          この受注は会員に帰属していないため、訂正で動くポイントはありません。伝票の申領が後から
          起きた場合、付与されるのは完了時点の会計に基づく額です。
        </p>
      ) : (
        <p className="text-muted-foreground text-sm">
          付与は完了時点の合計に基づく事実なので、この訂正では動きません。この受注は会員コード{' '}
          {memberCode} へ帰属しています。手当ての要否は会員ポイントの台帳で確かめてください。
        </p>
      )}
    </div>
  );
}

/**
 * 完了した受注の訂正ページ（ADR 0019）。入口はアーカイブの完了行で、ORDER_CORRECT 保持者にだけ見える。
 *
 * <p>直せるのは実績時刻・コーススナップショット・明細行の三組だけ。それ以外（予定時刻・人数・指名・
 * 受付担当・備考・伝言）は終端状態の凍結のままで、この画面には欄そのものが無い。
 *
 * <p>送るのは<b>三組の全量</b>で、部分更新ではない。空にした欄はそのまま空になる — 実終了時刻や
 * 延長分数を空へ戻す訂正が要るため、「送らない＝変更しない」の形では表せない。
 *
 * <p>門はポイントを動かさない。訂正で合計が変われば付与との差が生まれるので、送信後にその差額と
 * 手当ての行き先を示す。
 */
export default function OrderCorrectionPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const orderId = params.id as string;
  const router = useRouter();

  const {
    data: current,
    isLoading,
    failure,
    reload,
  } = useResource<Order>(() => orderApi.get(orderId), [orderId]);

  const form = useForm<OrderCorrectionFormValues>({ defaultValues: EMPTY_VALUES });
  const { handleSubmit, control, reset, watch, formState } = form;
  const [hasSeeded, setHasSeeded] = useState(false);
  const [outcome, setOutcome] = useState<CorrectionOutcomeState | null>(null);

  // 取得の到着はレンダーより後なので、初期値ではなく効果で播く（開いた最初のフレームが空欄になる）。
  useEffect(() => {
    if (current === null) {
      return;
    }
    reset({
      actual_arrival_time: toTimeInput(current.actual_arrival_time),
      actual_end_time: toTimeInput(current.actual_end_time),
      course_name: current.course_name ?? '',
      course_minutes: current.course_minutes != null ? String(current.course_minutes) : '',
      extension_minutes: current.extension_minutes != null ? String(current.extension_minutes) : '',
      fee_lines: storeEditableFeeLines(current.fee_lines),
      reason: '',
    });
    setHasSeeded(true);
  }, [current, reset]);

  const seeded = current !== null && hasSeeded;
  const completed = current?.status === 'COMPLETED';

  /**
   * この受注が現に帰属している会員のコード。帰属していなければ null。
   *
   * 手当ての宛先は帰属記録であって、顧客に今紐づく会員ではない（ADR 0012）。読めなかったときは
   * 帰属している側へ倒す — 「動くポイントはありません」と誤って言い切ると、実際に残った誤付与に
   * 気づく機会がどこにも無くなる。宛先が名乗れないぶんは不明として示す。
   */
  const attributedMemberCode = async (): Promise<string | null> => {
    try {
      const attribution = await orderApi.attribution(orderId);
      return attribution.attributed ? (attribution.member_code ?? '不明') : null;
    } catch {
      return '不明';
    }
  };

  const submit = async (values: OrderCorrectionFormValues) => {
    if (current === null) {
      return;
    }
    try {
      const corrected = await orderApi.correct(orderId, {
        // 開いた時点の版をそのまま返す。全量を送る口なので、間に別の操作者の訂正が挟まっていれば
        // サーバが 409 で差し戻す（読み直さずに送ると、その訂正を黙って巻き戻す）
        expected_version: current.version,
        reason: values.reason.trim(),
        actual_arrival_time: optionalTime(values.actual_arrival_time),
        actual_end_time: optionalTime(values.actual_end_time),
        course_name: values.course_name.trim() === '' ? undefined : values.course_name.trim(),
        course_minutes: optionalNumber(values.course_minutes),
        extension_minutes: optionalNumber(values.extension_minutes),
        fee_lines: toFeeLineInputs(values.fee_lines),
      });
      notify.success('受注を訂正しました');
      // 結果は差し替えではなく併記で残す。付与差額はこの応答にしか現れず、一覧へ戻すと手当ての
      // 必要に気づく機会が消える。
      setOutcome({ result: corrected, memberCode: await attributedMemberCode() });
      await reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '受注の訂正に失敗しました'));
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-foreground text-2xl font-bold">完了後の訂正</h1>
        {current !== null && (
          <p className="text-muted-foreground mt-1 text-sm">
            {customerHeadingText(current)}
            {current.business_date ? ` ・ ${current.business_date}` : ''}
          </p>
        )}
      </div>

      {isLoading && <p className="text-muted-foreground text-sm">読み込み中...</p>}
      {failure === 'error' && (
        <RegionError message="受注を取得できませんでした。" onRetry={() => void reload()} />
      )}
      {failure === 'notFound' && (
        <RegionError
          message="この受注は見つかりませんでした。"
          fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
        />
      )}

      {outcome !== null && <CorrectionOutcome outcome={outcome} />}

      {/* 完了していない受注はサーバが撥ねる。欄を出してから 400 を返すより、開いた時点で理由を名乗る */}
      {seeded && !completed && (
        <RegionError
          message="完了した受注だけが訂正できます。確定済みの受注は編集画面から、取消済みの受注は同じ内容で起こし直してください。"
          fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
        />
      )}

      {seeded && completed && (
        <Form {...form}>
          <form onSubmit={handleSubmit(submit)} className="space-y-6">
            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">実績</h2>
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={control}
                  name="actual_arrival_time"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>実際の到着</FormLabel>
                      <FormControl>
                        <Input type="time" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="actual_end_time"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>実際の終了</FormLabel>
                      <FormControl>
                        <Input type="time" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">コース</h2>
              <div className="grid grid-cols-3 gap-4">
                <FormField
                  control={control}
                  name="course_name"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>コース名</FormLabel>
                      <FormControl>
                        <Input type="text" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="course_minutes"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>コース（分）</FormLabel>
                      <FormControl>
                        <Input type="number" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="extension_minutes"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>延長（分）</FormLabel>
                      <FormControl>
                        <Input type="number" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">会計</h2>
              {/* ポイント利用の行は門内でも編集不可。読み取りで並べるだけ（誤りはポイント機構で直す） */}
              <OrderFeeLinesField
                systemLines={systemOwnedFeeLines(current.fee_lines)}
                courseName={watch('course_name')}
              />
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">訂正の理由</h2>
              <FormField
                control={control}
                name="reason"
                rules={{
                  validate: value => value.trim() !== '' || '訂正の理由を入力してください',
                  maxLength: { value: 500, message: '訂正の理由は 500 文字以内で入力してください' },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>理由</FormLabel>
                    <FormControl>
                      <Textarea rows={3} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <p className="text-muted-foreground text-xs">
                確定した記録を動かす操作なので、理由・実行者・時刻と訂正前の内容が履歴に残ります。
              </p>
            </section>

            <div className="flex justify-end gap-4">
              <Button
                type="button"
                variant="outline"
                disabled={formState.isSubmitting}
                onClick={() => router.push(storePath(storeId, '/orders'))}
              >
                一覧へ戻る
              </Button>
              <Button type="submit" disabled={formState.isSubmitting}>
                {formState.isSubmitting ? '訂正中...' : '訂正する'}
              </Button>
            </div>
          </form>
        </Form>
      )}
    </div>
  );
}
