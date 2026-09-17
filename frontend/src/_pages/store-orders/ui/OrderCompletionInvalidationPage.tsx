'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { orderApi, type OrderCorrectionHistoryEntry } from '@/entities/order';
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
import { formatDateTime } from '../lib/formatDateTime';

export default function OrderCompletionInvalidationPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const id = params.id as string;
  return <Invalidation key={`${storeId}:${id}`} storeId={storeId} id={id} />;
}

function Invalidation({ storeId, id }: { storeId: string; id: string }) {
  const [allowed, setAllowed] = useState<boolean | null>(null);
  const [canCorrect, setCanCorrect] = useState(false);
  const [canAdjust, setCanAdjust] = useState(false);
  useEffect(() => {
    const claims = readTokenClaims();
    setAllowed(hasPermission(claims, 'ORDER_MANAGE'));
    setCanCorrect(hasPermission(claims, 'ORDER_CORRECT'));
    setCanAdjust(hasPermission(claims, 'POINT_ADJUST'));
  }, []);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const { data, failure, isLoading, reload } = useResource(
    allowed
      ? async () => {
          try {
            const order = await orderApi.get(id);
            let change: OrderCorrectionHistoryEntry | undefined;
            if (order.completion_invalidated) {
              // 無効化後は訂正できないため、無効化は版降順の履歴の先頭ページにある。
              const history = await orderApi.correctionHistory('store', id);
              change = history.rows.find(entry => entry.change_type === 'COMPLETION_INVALIDATION');
              if (!change) throw new Error('無効化履歴が見つかりません');
            }
            return { order, change };
          } catch (error) {
            if (isForbidden(error)) {
              setAllowed(false);
              return null;
            }
            throw error;
          }
        }
      : null,
    [allowed, id, storeId]
  );
  const form = useForm<{ reason: string }>({ defaultValues: { reason: '' } });
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<OrderCorrectionHistoryEntry | null>(null);
  const order = data?.order;
  const change = result ?? data?.change;
  const changeId = change?.correction_id;
  const [error, setError] = useState<string | null>(null);
  const remaining =
    order?.fee_lines.reduce(
      (sum, line) =>
        sum +
        (line.kind === 'POINT_REDEMPTION'
          ? line.amount
          : line.kind === 'POINT_REDEMPTION_OFFSET'
            ? -line.amount
            : 0),
      0
    ) ?? 0;
  const invalidated = !!changeId || order?.completion_invalidated;
  const submit = async () => {
    if (!canCorrect || confirmation === null || !order || order.version === undefined || submitting)
      return;
    setSubmitting(true);
    setConfirmation(null);
    setError(null);
    try {
      const result = await orderApi.invalidateCompletion(id, {
        expected_version: order.version,
        reason: confirmation,
      });
      if (!active.current) return;
      setResult(result);
      notify.success('誤完了を無効化しました');
    } catch (e) {
      if (!active.current) return;
      if (isForbidden(e)) setAllowed(false);
      else {
        setError(getApiErrorMessage(e, '無効化できませんでした。内容を確認してください。'));
        if (isConflict(e)) await reload();
      }
    } finally {
      if (active.current) setSubmitting(false);
    }
  };
  if (allowed === false)
    return (
      <RegionError
        message="ORDER_MANAGE と ORDER_CORRECT を持つ訂正担当者へ依頼してください。"
        fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
      />
    );
  return (
    <div className="space-y-6">
      <h1 className="text-foreground text-2xl font-bold">誤完了の無効化</h1>
      {(allowed === null || isLoading) && <p>読み込み中...</p>}
      {failure === 'error' && (
        <RegionError message="受注を取得できませんでした。" onRetry={() => void reload()} />
      )}
      {failure === 'notFound' && (
        <RegionError
          message="受注が見つかりません。"
          fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
        />
      )}
      {order && !failure && !isLoading && (
        <>
          <section className="bg-card space-y-3 rounded-xl border p-6">
            <h2 className="font-medium">{invalidated ? '無効化済み・原記録' : '確認する原記録'}</h2>
            <p>
              原営業日 {order.business_date} / 原完了日時{' '}
              {order.completed_at ? formatDateTime(order.completed_at) : '未完了'}
            </p>
            <p>{order.course.name}</p>
            <ul>
              {order.fee_lines.map((line, index) => (
                <li key={line.line_id ?? index}>
                  {line.name}：
                  {line.kind === 'DISCOUNT' || line.kind === 'POINT_REDEMPTION' ? '−' : ''}
                  {line.amount.toLocaleString()} 円 / 原報酬 {line.remuneration.toLocaleString()} 円
                </li>
              ))}
            </ul>
            <p>
              有効な請求 {(change?.before.total_fee ?? order.total_fee ?? 0).toLocaleString()} 円 →
              0 円
            </p>
            <p>
              発生済み報酬{' '}
              {(change?.before.accrued_remuneration ?? order.accrued_remuneration).toLocaleString()}{' '}
              円 → 0 円
            </p>
            <p>
              会員帰属・ポイント付与・実返金・支払は変更しません。それぞれの担当者が専用の操作で処置してください。
            </p>
          </section>
          {changeId && <p>変更 ID：{changeId}</p>}
          {invalidated ? (
            <>
              <p>完了の原記録を保持しています。復活・通常訂正はできません。</p>
              <Button
                render={
                  <Link
                    href={storePath(
                      storeId,
                      `/orders/create?replacement_for_order_id=${encodeURIComponent(id)}`
                    )}
                  />
                }
              >
                関連する新受注で再提供
              </Button>
            </>
          ) : !canCorrect ? (
            <p role="alert">ORDER_MANAGE と ORDER_CORRECT を持つ訂正担当者へ依頼してください。</p>
          ) : order.status !== 'COMPLETED' ? (
            <p role="alert">完了した受注だけが無効化できます。</p>
          ) : remaining > 0 ? (
            <div role="alert" className="space-y-3">
              <p>
                利用ポイントが {remaining.toLocaleString()} pt
                残っています。ポイント救済担当者が利用取消を完了した後、ここで再取得して無効化を再実行してください。
              </p>
              {canAdjust ? (
                <Button render={<Link href={storePath(storeId, `/orders/${id}/point-rollback`)} />}>
                  ポイント救済へ
                </Button>
              ) : (
                <p>ORDER_MANAGE と POINT_ADJUST を持つポイント救済担当者へ依頼してください。</p>
              )}
              <Button variant="outline" onClick={() => void reload()}>
                処置後に再取得
              </Button>
            </div>
          ) : (
            <Form {...form}>
              <form
                noValidate
                className="space-y-4"
                onSubmit={form.handleSubmit(data => setConfirmation(data.reason.trim()))}
              >
                <FormField
                  control={form.control}
                  name="reason"
                  rules={{
                    validate: value =>
                      (value.trim().length > 0 && value.trim().length <= 500) ||
                      '理由は 1〜500 文字で入力してください',
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>理由</FormLabel>
                      <FormControl>
                        <Textarea {...field} required />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="submit" disabled={submitting}>
                  無効化を確認
                </Button>
              </form>
            </Form>
          )}
        </>
      )}
      {error && <p role="alert">{error} 内容を再確認してから操作してください。</p>}
      <Button variant="outline" render={<Link href={storePath(storeId, `/orders/${id}/edit`)} />}>
        受注詳細へ
      </Button>
      <ConfirmDialog
        open={confirmation !== null}
        onClose={() => setConfirmation(null)}
        title="全く提供していない受注ですか？"
        description={`理由：${confirmation ?? ''}。有効な費用・報酬・請求を零にします。元に戻せません。`}
        confirmLabel="未提供を確認して無効化"
        onConfirm={() => void submit()}
      />
    </div>
  );
}
