'use client';

import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  Order,
  OrderPointRollbackPreview,
  OrderPointRollbackResult,
  orderApi,
} from '@/entities/order';
import { getApiErrorMessage, isConflict, storePath, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { customerHeadingText } from '../lib/customerLabel';
import {
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

interface RollbackFormValues {
  reason: string;
}

/** 巻き戻しで動く量の並び。実行前は下見、実行後は実績で同じ形を描く。 */
function PointsSummary({ cancelled, restored }: { cancelled: number; restored: number }) {
  return (
    <dl className="grid grid-cols-2 gap-4 text-sm">
      <div>
        <dt className="text-muted-foreground">取り消す付与</dt>
        <dd className="text-foreground text-lg font-medium">{cancelled.toLocaleString()} pt</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">利用者へ戻る利用</dt>
        <dd className="text-foreground text-lg font-medium">{restored.toLocaleString()} pt</dd>
      </div>
    </dl>
  );
}

/**
 * 受注 1 件を宛先にポイントの授受を打ち消す操作面（ADR 0023）。入口は完了後訂正の画面で、
 * POINT_ADJUST 保持者にだけ見える。
 *
 * 完了後訂正の門とは機構が繋がっていない — 門は台帳を読みも書きもせず、ここへの導線は画面レベルに
 * 留まる。訂正で会計が変わっても付与は動かないので、全否定が要るときだけこの操作を起こす。
 *
 * 巻き戻した受注は伝票トークンの事後申領を永久に拒む。誤帰属の清掃に使うと、正しい本人のその後の
 * 申領まで塞がれるため、その用途は帰属訂正の側にあることを面でも名乗る。
 */
export default function OrderPointRollbackPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const orderId = params.id as string;
  const router = useRouter();

  const {
    data: order,
    failure: orderFailure,
    reload: reloadOrder,
  } = useResource<Order>(() => orderApi.get(orderId), [orderId]);
  const {
    data: preview,
    isLoading,
    failure,
    reload,
  } = useResource<OrderPointRollbackPreview>(
    () => orderApi.pointRollbackPreview(orderId),
    [orderId]
  );

  const form = useForm<RollbackFormValues>({ defaultValues: { reason: '' } });
  const { handleSubmit, control, formState } = form;
  const [result, setResult] = useState<OrderPointRollbackResult | null>(null);

  const completed = order?.status === 'COMPLETED';
  const done = preview?.already_rolled_back === true;

  const submit = async (values: RollbackFormValues) => {
    try {
      setResult(await orderApi.pointRollback(orderId, { reason: values.reason.trim() }));
      notify.success('ポイントを巻き戻しました');
      await reload();
    } catch (error) {
      if (isConflict(error)) {
        // 二度目は撥ねられる。初回の理由・実行者はそのまま残っているので、下見を取り直して
        // 「済み」の姿へ落とす（その場の再送は何度でも 409 になる）
        notify.warning('この受注のポイントは既に巻き戻されています');
        await reload();
        return;
      }
      notify.error(getApiErrorMessage(error, 'ポイントの巻き戻しに失敗しました'));
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-foreground text-2xl font-bold">ポイントの巻き戻し</h1>
        {order !== null && (
          <p className="text-muted-foreground mt-1 text-sm">
            {customerHeadingText(order)}
            {order.business_date ? ` ・ ${order.business_date}` : ''}
          </p>
        )}
      </div>

      {isLoading && <p className="text-muted-foreground text-sm">読み込み中...</p>}
      {(failure === 'error' || orderFailure === 'error') && (
        // どちらが落ちたかは画面の姿を変えないので、再試行は両方を取り直す。片方だけだと、
        // 落ちたのがもう一方だったときに押しても何も直らない
        <RegionError
          message="受注を取得できませんでした。"
          onRetry={() => void Promise.all([reloadOrder(), reload()])}
        />
      )}
      {(failure === 'notFound' || orderFailure === 'notFound') && (
        <RegionError
          message="この受注は見つかりませんでした。"
          fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
        />
      )}

      {order !== null && !completed && (
        <RegionError
          message="完了した受注だけがポイントを巻き戻せます。付与も利用も完了と伝票の申領でしか記帳されないため、確定済み・取消済みの受注に打ち消すものはありません。"
          fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
        />
      )}

      {result !== null && (
        <div className="bg-card space-y-3 rounded-xl border p-4">
          <h2 className="text-foreground text-sm font-medium">巻き戻しました</h2>
          <PointsSummary cancelled={result.cancelled_points} restored={result.restored_points} />
          <p className="text-muted-foreground text-sm">
            {result.restored_points > 0
              ? '戻した利用は元のロットへ期限そのまま返っています。'
              : '台帳に打ち消す対象はありませんでした。'}
            この受注は以後、伝票の申領を受け付けません。
          </p>
        </div>
      )}

      {preview !== null && completed && (
        <div className="bg-card space-y-4 rounded-xl border p-4">
          <h2 className="text-foreground text-sm font-medium">この操作で動くポイント</h2>
          <PointsSummary
            cancelled={preview.cancellable_points}
            restored={preview.reversible_used_points}
          />
          <p className="text-muted-foreground text-sm">
            {preview.member_code === undefined
              ? 'この受注は会員に帰属していません。台帳に仕訳が無くても、巻き戻すと伝票の申領は受け付けなくなります。'
              : `この受注は会員コード ${preview.member_code} へ帰属しています。過去に別の会員へ帰属していた分の付与も、まとめて取り消されます。`}
          </p>
          {done && (
            <p className="text-muted-foreground text-sm">
              この受注は既に巻き戻し済みです。二度目は受け付けません（初回の理由と実行者はそのまま残ります）。
            </p>
          )}
        </div>
      )}

      {preview !== null && completed && !done && (
        <Form {...form}>
          <form onSubmit={handleSubmit(submit)} className="space-y-6">
            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">巻き戻しの理由</h2>
              <FormField
                control={control}
                name="reason"
                rules={{
                  validate: value => value.trim() !== '' || '巻き戻しの理由を入力してください',
                  maxLength: {
                    value: 500,
                    message: '巻き戻しの理由は 500 文字以内で入力してください',
                  },
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
                消費し切った付与は取り戻せません。差額の手当ては会員ポイントの手動調整、誤った相手への付与は
                会員帰属の訂正が担います。誤帰属の清掃にこの操作を使うと、正しい本人の申領まで塞がれます。
              </p>
            </section>

            <div className="flex justify-end gap-4">
              <Button
                type="button"
                variant="outline"
                disabled={formState.isSubmitting}
                onClick={() => router.push(storePath(storeId, `/orders/${orderId}/correction`))}
              >
                訂正画面へ戻る
              </Button>
              <Button type="submit" disabled={formState.isSubmitting}>
                {formState.isSubmitting ? '巻き戻し中...' : '巻き戻す'}
              </Button>
            </div>
          </form>
        </Form>
      )}
    </div>
  );
}
