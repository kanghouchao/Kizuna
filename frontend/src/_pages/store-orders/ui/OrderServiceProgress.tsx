'use client';

import { useRef, useState } from 'react';
import { useStoreContext } from '@/entities/user';
import { Order, orderApi, ORDER_STATUS_LABELS } from '@/entities/order';
import {
  getApiErrorMessage,
  KeyedResource,
  useKeyedResource,
  useVersionedWrite,
} from '@/shared/lib';
import { Button, Input, Label, Pager, RegionError } from '@/shared/ui';
import { orderConflictField } from './useOrderConfirmation';

/** 編集画面と同じ受注の版を送るので、書き込みは編集画面のものを共有する。409 はどちらの送信も塞ぐ。 */
type OrderWriter = Pick<ReturnType<typeof useVersionedWrite>, 'busy' | 'conflict' | 'write'>;

export function OrderServiceProgress({
  order,
  writer,
  onOrderUpdated,
  onReload,
}: {
  order: Order;
  writer: OrderWriter;
  onOrderUpdated: (order: Order) => void;
  /** 入力を残したまま受注を取り直す。取り直しは共有の書き込みを通し、競合を畳む */
  onReload: KeyedResource<Order>['reload'];
}) {
  const store = useStoreContext().currentStoreId;
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(false);
  const reasonInput = useRef<HTMLInputElement>(null);
  const { busy } = writer;
  const [error, setError] = useState<string>();
  const [page, setPage] = useState(0);
  const history = useKeyedResource(
    ['special-service-events', store, order.id, order.version, page],
    order.id ? () => orderApi.specialServiceEvents(order.id!, page) : null
  );
  const start = async () => {
    let versionConflict = false;
    const outcome = await writer.write(
      () => orderApi.start(order.id!, order.version!, reason.trim()),
      {
        // 失敗は toast ではなく、この節の中に出す
        failure: (e, kind) => {
          versionConflict = kind === 'conflict' && orderConflictField(e) === 'expected_version';
        },
        onSuccess: onOrderUpdated,
        onFailure: e => {
          if (!versionConflict) {
            setError(getApiErrorMessage(e, '開始できませんでした。最新の受注を確認してください。'));
          }
        },
      }
    );
    if (outcome !== 'conflict') return;
    const result = await onReload();
    if (versionConflict && result.status === 'success' && result.isCurrent()) {
      setError('最新の受注を読み込みました。内容を確認してから開始を再試行してください。');
    }
  };
  return (
    <section
      className="grid gap-6 rounded-xl border bg-card p-6 text-sm md:grid-cols-2"
      aria-label="サービスの進行"
    >
      <div className="space-y-3">
        <h2 className="font-medium">サービスの進行</h2>
        <p>
          {ORDER_STATUS_LABELS[order.status ?? 'CONFIRMED']}
          {order.started_at ? ` / 開始 ${new Date(order.started_at).toLocaleString('ja-JP')}` : ''}
        </p>
        {order.requires_attention && (
          <p role="alert" className="text-destructive-strong">
            本人拒否の未処理項目があります。下の特殊サービスを修復して保存するまで開始・完了できません。
          </p>
        )}
        {order.status === 'CONFIRMED' && (
          <div className="space-y-2">
            <Label htmlFor="start-reason">開始の理由</Label>
            <Input
              id="start-reason"
              ref={reasonInput}
              aria-invalid={reasonError}
              aria-describedby={reasonError ? 'start-reason-error' : undefined}
              maxLength={500}
              value={reason}
              onChange={e => {
                setReason(e.target.value);
                setReasonError(false);
              }}
            />
            {reasonError && (
              <p id="start-reason-error" role="alert">
                開始の理由を入力してください
              </p>
            )}
            <Button
              type="button"
              disabled={
                busy || writer.conflict || order.requires_attention || order.version === undefined
              }
              onClick={() => {
                if (!reason.trim()) {
                  setReasonError(true);
                  reasonInput.current?.focus();
                  return;
                }
                setError(undefined);
                void start();
              }}
            >
              サービスを開始
            </Button>
          </div>
        )}
        {error && <p role="alert">{error}</p>}
      </div>
      <div className="space-y-3 border-t pt-6 md:border-t-0 md:border-l md:pt-0 md:pl-6">
        <h3 className="font-medium">拒否・処置履歴</h3>
        {history.isLoading ? (
          <p>履歴を読み込み中...</p>
        ) : history.failure ? (
          <RegionError
            message="履歴を取得できませんでした。権限を確認して再試行してください。"
            onRetry={() => void history.reload()}
          />
        ) : (
          <>
            <ul className="space-y-2">
              {history.data?.rows.map(event => (
                <li key={event.id}>
                  {new Date(event.occurred_at).toLocaleString('ja-JP')} /{' '}
                  {event.kind === 'REJECTED' ? '本人拒否' : '処置済み'} /{' '}
                  {event.resolution === 'CAST_CHANGED'
                    ? '担当変更'
                    : event.resolution === 'RESELECTED'
                      ? '改選'
                      : event.resolution === 'REMOVED'
                        ? '除去'
                        : ''}
                  <p>
                    請求 ¥{event.previous_total_fee.toLocaleString()} → ¥
                    {event.total_fee.toLocaleString()}
                  </p>
                  <p>
                    {event.before.map(item => item.name).join('、')} →{' '}
                    {event.after.map(item => item.name).join('、') || 'なし'}
                  </p>
                </li>
              ))}
            </ul>
            {history.data?.rows.length === 0 && <p>拒否・処置の記録はありません。</p>}
            <Pager
              page={page}
              pageCount={history.data?.pageCount ?? 0}
              total={history.data?.total ?? 0}
              rowCount={history.data?.rows.length ?? 0}
              disabled={history.isLoading}
              onPageChange={setPage}
            />
          </>
        )}
      </div>
    </section>
  );
}
