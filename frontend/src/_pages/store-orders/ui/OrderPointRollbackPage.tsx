'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { OrderPointRollbackResult, orderApi } from '@/entities/order';
import {
  getApiErrorMessage,
  hasPermission,
  isConflict,
  isForbidden,
  readTokenClaims,
  storePath,
  useResource,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  ConfirmDialog,
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

interface RollbackFormValues {
  reason: string;
}

function Billing({ before, offset, after }: { before: number; offset: number; after: number }) {
  return (
    <dl className="grid grid-cols-3 gap-4 text-sm">
      <div>
        <dt>処置前の請求</dt>
        <dd>{before.toLocaleString()} 円</dd>
      </div>
      <div>
        <dt>システム相殺</dt>
        <dd>+{offset.toLocaleString()} 円</dd>
      </div>
      <div>
        <dt>処置後の請求</dt>
        <dd>{after.toLocaleString()} 円</dd>
      </div>
    </dl>
  );
}

export default function OrderPointRollbackPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const orderId = params.id as string;
  return <RollbackPage key={`${storeId}:${orderId}`} storeId={storeId} orderId={orderId} />;
}

function RollbackPage({ storeId, orderId }: { storeId: string; orderId: string }) {
  const [allowed, setAllowed] = useState<boolean | null>(null);
  useEffect(() => {
    const claims = readTokenClaims();
    setAllowed(hasPermission(claims, 'ORDER_MANAGE') && hasPermission(claims, 'POINT_ADJUST'));
  }, []);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const { data, isLoading, failure, reload } = useResource(
    allowed
      ? async () => {
          try {
            const order = await orderApi.get(orderId);
            const preview =
              order.status === 'COMPLETED' ? await orderApi.pointRollbackPreview(orderId) : null;
            return { denied: false as const, order, preview };
          } catch (error) {
            if (isForbidden(error)) return { denied: true as const };
            throw error;
          }
        }
      : null,
    [allowed, orderId, storeId]
  );
  const form = useForm<RollbackFormValues>({ defaultValues: { reason: '' } });
  const [confirmation, setConfirmation] = useState<RollbackFormValues | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<OrderPointRollbackResult | null>(null);
  const order = data && !data.denied ? data.order : null;
  const preview = data && !data.denied ? data.preview : null;
  const history = result ?? preview?.rollback;
  const submit = async () => {
    if (!confirmation || !preview || submitting) return;
    setSubmitting(true);
    setConfirmation(null);
    try {
      const response = await orderApi.pointRollback(orderId, {
        reason: confirmation.reason.trim(),
        expected_total_fee: preview.current_total_fee,
        expected_offset_amount: preview.offset_amount,
      });
      if (!active.current) return;
      setResult(response);
      notify.success('ポイントを巻き戻しました');
      await reload();
    } catch (error) {
      if (!active.current) return;
      if (isForbidden(error)) setAllowed(false);
      else if (isConflict(error)) {
        notify.warning(getApiErrorMessage(error, '内容が変わりました。再確認してください'));
        await reload();
      } else notify.error(getApiErrorMessage(error, 'ポイントの巻き戻しに失敗しました'));
    } finally {
      if (active.current) setSubmitting(false);
    }
  };

  if (allowed === false || data?.denied)
    return (
      <RegionError
        message="受注管理（ORDER_MANAGE）と POINT_ADJUST を持つポイント救済担当者へ依頼してください。この画面が専用のポイント巻き戻し入口です。"
        fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
      />
    );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-foreground text-2xl font-bold">ポイントの巻き戻し</h1>
        {order && (
          <p className="text-muted-foreground mt-1 text-sm">
            {customerHeadingText(order)} ・ {order.business_date}
          </p>
        )}
      </div>
      {(allowed === null || isLoading) && (
        <p className="text-muted-foreground text-sm">読み込み中...</p>
      )}
      {failure === 'error' && (
        <RegionError message="受注を取得できませんでした。" onRetry={() => void reload()} />
      )}
      {failure === 'notFound' && (
        <RegionError
          message="この受注は見つかりませんでした。"
          fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
        />
      )}
      {order && order.status !== 'COMPLETED' && (
        <RegionError
          message="完了した受注だけがポイントを巻き戻せます。"
          fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
        />
      )}
      {history && (
        <section className="bg-card space-y-4 rounded-xl border p-6 shadow-sm">
          <h2 className="text-foreground font-medium">
            {result ? '巻き戻しました' : '巻き戻し履歴'}
          </h2>
          <Billing
            before={history.before_total_fee}
            offset={history.offset_amount}
            after={history.after_total_fee}
          />
          <p>
            取消付与 {history.cancelled_points.toLocaleString()} pt ・ 返還利用{' '}
            {history.restored_points.toLocaleString()} pt
          </p>
          <p>
            {history.restored_points > 0
              ? '戻した利用は元のロットへ期限そのまま返っています。'
              : history.cancelled_points > 0
                ? '未消費の付与を取り消しました。逆転する利用はありませんでした。'
                : '台帳に打ち消す対象はありませんでした。'}
          </p>
          <p>理由：{history.reason}</p>
          <p>
            操作者：{history.actor_user_id} ・ {history.created_at}
          </p>
          <p className="text-muted-foreground text-sm">
            操作記録：{history.id}。この受注は以後、伝票の申領を受け付けません。
          </p>
        </section>
      )}
      {preview && order?.status === 'COMPLETED' && !isLoading && !failure && (
        <>
          {preview.already_rolled_back || result ? (
            <p>この受注は既に巻き戻し済みです。二度目は受け付けません。</p>
          ) : (
            <>
              <section className="bg-card space-y-4 rounded-xl border p-6 shadow-sm">
                <h2 className="font-medium">この操作で動くポイントと請求</h2>
                <dl className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <dt>取り消す付与</dt>
                    <dd>{preview.cancellable_points.toLocaleString()} pt</dd>
                  </div>
                  <div>
                    <dt>利用者へ戻る利用</dt>
                    <dd>{preview.reversible_used_points.toLocaleString()} pt</dd>
                  </div>
                </dl>
                <Billing
                  before={preview.current_total_fee}
                  offset={preview.offset_amount}
                  after={preview.resulting_total_fee}
                />
                <p className="text-muted-foreground text-sm">
                  {preview.member_code === undefined
                    ? 'この受注は会員に帰属していません。'
                    : `この受注は会員コード ${preview.member_code} へ帰属しています。`}
                  巻き戻すと伝票の申領は受け付けなくなります。元の利用明細は保持されます。
                </p>
              </section>
              <Form {...form}>
                <form
                  noValidate
                  onSubmit={form.handleSubmit(setConfirmation)}
                  className="space-y-6"
                >
                  <FormField
                    control={form.control}
                    name="reason"
                    rules={{
                      validate: value =>
                        value.trim() === ''
                          ? '巻き戻しの理由を入力してください'
                          : value.trim().length <= 500 ||
                            '巻き戻しの理由は 500 文字以内で入力してください',
                    }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>理由</FormLabel>
                        <FormControl>
                          <Textarea {...field} rows={3} required />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <p className="text-muted-foreground text-sm">
                    消費済みの付与は取り戻せません。誤った会員への帰属は会員帰属の訂正で扱います。
                  </p>
                  <div className="flex justify-end">
                    <Button type="submit" disabled={submitting}>
                      {submitting ? '巻き戻し中...' : '巻き戻す'}
                    </Button>
                  </div>
                </form>
              </Form>
            </>
          )}
        </>
      )}
      <Button variant="outline" render={<Link href={storePath(storeId, '/orders')} />}>
        オーダー一覧へ
      </Button>
      <ConfirmDialog
        open={confirmation !== null}
        onClose={() => setConfirmation(null)}
        title="ポイントを巻き戻しますか？"
        description={
          preview
            ? `請求は ${preview.current_total_fee.toLocaleString()} 円から ${preview.resulting_total_fee.toLocaleString()} 円になります。この受注は以後、伝票の申領を受け付けません。`
            : ''
        }
        confirmLabel="実行する"
        onConfirm={() => void submit()}
      />
    </div>
  );
}
