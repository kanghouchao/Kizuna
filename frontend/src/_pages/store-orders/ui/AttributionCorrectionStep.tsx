'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { OrderAttributionCorrection, orderApi } from '@/entities/order';
import { getApiErrorMessage, integerRule, useResource } from '@/shared/lib';
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

/** 理由の上限。台帳の理由欄（@Size(max = 500)）と同値。 */
const REASON_MAX_LENGTH = 500;

const POINTS_REQUIRED = '差し引くポイントを入力してください';

interface AttributionCorrectionStepProps {
  orderId: string;
  /** 訂正の宛先を決める帰属記録。差し引く相手はこの記録が持つ会員で、顧客に今紐づく会員ではない。 */
  attributionId: number;
  memberCode?: string;
  /** 送信中かどうかを親へ上げる。親はこれを見てダイアログの取り消し（ESC・背景押下）を塞ぐ。 */
  onBusyChange: (isBusy: boolean) => void;
  onDone: () => void;
}

/**
 * 無効化に続く訂正の段（ADR 0012）。
 *
 * 無効化しただけでは、誤って付与されたポイントはその会員の台帳に残ったままになる。領域としては独立した
 * 二段目だが、画面では一段目の直後にここへ送る — 同じ操作者の同じ会話の中で閉じないと、やり残しに気づく
 * 機会がどこにも無いためである。
 */
export function AttributionCorrectionStep({
  orderId,
  attributionId,
  memberCode,
  onBusyChange,
  onDone,
}: AttributionCorrectionStepProps) {
  const { data, isLoading, failure, reload } = useResource<OrderAttributionCorrection>(
    async () => orderApi.attributionCorrection(orderId, attributionId),
    [orderId, attributionId]
  );

  if (isLoading) {
    return <p className="px-6 py-8 text-center text-sm text-muted-foreground">読み込み中...</p>;
  }
  if (failure !== null) {
    return (
      <RegionError
        message="訂正の状況を取得できませんでした"
        onRetry={() => void reload()}
        className="justify-center px-6 py-8"
      />
    );
  }
  if (data === null) {
    return null;
  }
  // 取得できてからフォームを起こす。既定値は取得値から作るため、先に組むと最初の 1 レンダーだけ空欄になる。
  return (
    <CorrectionForm
      orderId={orderId}
      attributionId={attributionId}
      memberCode={memberCode}
      status={data}
      onBusyChange={onBusyChange}
      onDone={onDone}
    />
  );
}

interface CorrectionFormProps extends AttributionCorrectionStepProps {
  status: OrderAttributionCorrection;
}

interface CorrectionFormValues {
  points: number;
  reason: string;
}

function CorrectionForm({
  orderId,
  attributionId,
  memberCode,
  status,
  onBusyChange,
  onDone,
}: CorrectionFormProps) {
  const remaining = status.granted_points - status.corrected_points;
  const form = useForm<CorrectionFormValues>({
    defaultValues: { points: remaining, reason: '' },
  });
  const {
    control,
    handleSubmit,
    formState: { isSubmitting },
  } = form;

  // 送信中を親へ上げる。片付けで必ず false へ戻すのは、成功時に onDone がこの段を畳むため —
  // 畳まれた瞬間の isSubmitting はまだ true で、通知だけでは親が送信中のまま固まる。
  useEffect(() => {
    onBusyChange(isSubmitting);
    return () => onBusyChange(false);
  }, [isSubmitting, onBusyChange]);

  // この段を開いた 1 回の人間の操作としてキーを発行する。失敗後の再送信でも変えない —
  // サーバはこのキーで「commit 済みの初回への再送」を見分けて二重記帳を遮断する（ADR 0007）。
  const [idempotencyKey] = useState(() => crypto.randomUUID());

  const submit = async (values: CorrectionFormValues) => {
    try {
      await orderApi.correctAttributionPoints(orderId, {
        attribution_id: attributionId,
        points: values.points,
        reason: values.reason,
        idempotency_key: idempotencyKey,
      });
      notify.success('誤って付与されたポイントを差し引きました');
      onDone();
    } catch (error) {
      // 残高不足・上限超過は、サーバが対処できる文言（引ける額を含む）を返す。汎用文言に潰さない。
      notify.error(getApiErrorMessage(error, 'ポイントの差し引きに失敗しました'));
    }
  };

  if (remaining <= 0) {
    return (
      <div className="space-y-4 px-6 py-5">
        <p className="text-sm text-muted-foreground">
          {status.granted_points === 0
            ? 'この来店ではポイントが付与されていないため、差し引く分はありません。'
            : '付与された分は差し引き済みです。'}
        </p>
        <div className="flex justify-end border-t pt-4">
          <Button type="button" onClick={onDone}>
            完了
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4 px-6 py-5">
      <p className="text-sm text-foreground">
        {memberCode
          ? `${memberCode} の台帳から差し引きます。`
          : '帰属していた会員の台帳から差し引きます。'}
        この来店の付与は {status.granted_points} ポイント
        {status.corrected_points > 0 &&
          `（うち ${status.corrected_points} ポイントは差し引き済み）`}
        です。
      </p>
      <Form {...form}>
        {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、我々の文言は
            永久に描かれない。required と下限は下の規則が引き継ぐ */}
        <form onSubmit={handleSubmit(submit)} className="space-y-4" noValidate>
          <FormField
            control={control}
            name="points"
            rules={{
              required: POINTS_REQUIRED,
              validate: {
                // 空欄は NaN であって null でも空文字でもないため required は素通りする
                notEmpty: value => !Number.isNaN(value) || POINTS_REQUIRED,
                // noValidate は type="number" の暗黙の step=1 も止める。これが無いと
                // 1.5 が int の points へ届く
                integer: integerRule('差し引くポイント'),
              },
              min: { value: 1, message: '差し引くポイントは 1 以上で入力してください' },
              max: {
                value: remaining,
                message: `差し引けるのは残り ${remaining} ポイントまでです`,
              },
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>差し引くポイント</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    required
                    min={1}
                    max={remaining}
                    {...field}
                    // register の valueAsNumber と同じ写像。Number() は空欄を 0 にしてしまい、
                    // 「未入力」を表す NaN が失われる。
                    value={Number.isNaN(field.value) ? '' : field.value}
                    onChange={event => field.onChange(event.target.valueAsNumber)}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={control}
            name="reason"
            rules={{
              required: '訂正の理由を入力してください',
              maxLength: {
                value: REASON_MAX_LENGTH,
                message: `理由は${REASON_MAX_LENGTH}文字以内で入力してください`,
              },
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>訂正の理由</FormLabel>
                <FormControl>
                  <Textarea rows={3} required maxLength={REASON_MAX_LENGTH} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          {/* 引き切れない場合があることは操作の手前で述べる。事後に気づくと、残った分が誰の目にも
              触れないまま訂正が済んだことになる */}
          <p className="text-sm text-muted-foreground">
            会員が既に使ってしまった分は差し引けません。残高が足りない場合は引ける額まで入れ直してください。
          </p>
          <div className="flex justify-end gap-3 border-t pt-4">
            <Button type="button" variant="outline" onClick={onDone} disabled={isSubmitting}>
              後で行う
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? '処理中...' : '差し引く'}
            </Button>
          </div>
        </form>
      </Form>
    </div>
  );
}
