'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { Order, orderApi, ORDER_STATUS_LABELS } from '@/entities/order';
import { getApiErrorMessage, useKeyedResource } from '@/shared/lib';
import { Button, Input, Label, RegionError } from '@/shared/ui';
import { orderConflictField } from './useOrderConfirmation';

export function OrderServiceProgress({
  order,
  onOrderUpdated,
}: {
  order: Order;
  onOrderUpdated: (order: Order) => void;
}) {
  const store = useParams()?.storeId as string;
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(false);
  const reasonInput = useRef<HTMLInputElement>(null);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [cursor, setCursor] = useState<string>();
  const history = useKeyedResource(
    ['special-service-events', store, order.id, order.version, cursor],
    order.id ? () => orderApi.specialServiceEvents(order.id!, cursor) : null
  );
  return (
    <section className="space-y-3 rounded-lg border p-4" aria-label="サービスの進行">
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
            disabled={saving || order.requires_attention || order.version === undefined}
            onClick={async () => {
              if (!reason.trim()) {
                setReasonError(true);
                reasonInput.current?.focus();
                return;
              }
              setSaving(true);
              setError(undefined);
              try {
                const updated = await orderApi.start(order.id!, order.version!, reason.trim());
                if (mounted.current) onOrderUpdated(updated);
              } catch (e) {
                if (!mounted.current) return;
                if (orderConflictField(e) === 'expected_version') {
                  try {
                    const latest = await orderApi.get(order.id!);
                    if (!mounted.current) return;
                    onOrderUpdated(latest);
                    setError(
                      '最新の受注を読み込みました。内容を確認してから開始を再試行してください。'
                    );
                  } catch (refreshError) {
                    if (mounted.current)
                      setError(
                        getApiErrorMessage(
                          refreshError,
                          '最新の受注を取得できませんでした。開始を再試行して再取得してください。'
                        )
                      );
                  }
                } else
                  setError(
                    getApiErrorMessage(e, '開始できませんでした。最新の受注を確認してください。')
                  );
              } finally {
                if (mounted.current) setSaving(false);
              }
            }}
          >
            サービスを開始
          </Button>
        </div>
      )}
      {error && <p role="alert">{error}</p>}
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
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={!cursor}
              onClick={() => setCursor(undefined)}
            >
              最初の履歴
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={!history.data?.nextCursor}
              onClick={() => setCursor(history.data?.nextCursor ?? undefined)}
            >
              次の履歴
            </Button>
          </div>
        </>
      )}
    </section>
  );
}
