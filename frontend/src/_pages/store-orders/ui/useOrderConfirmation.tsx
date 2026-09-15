'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { OrderPreview } from '@/entities/order';
import { getApiErrorMessage, isConflict, useKeyedResource } from '@/shared/lib';
import { Button, Dialog, DialogContent, DialogTitle, RegionError } from '@/shared/ui';

export function orderConflictField(error: unknown): string | undefined {
  if (!isConflict(error)) return undefined;
  const details = (error as { response?: { data?: { details?: Record<string, unknown> } } })
    .response?.data?.details;
  if (details?.confirmation_token) return 'confirmation_token';
  if (details?.expected_version) return 'expected_version';
  return undefined;
}

export function useOrderConfirmation(target?: string) {
  const params = useParams();
  const scope = `${params?.storeId}:${target ?? params?.id ?? ''}`;
  const [job, setJob] = useState<{
    load: () => Promise<OrderPreview>;
    key: number;
    scope: string;
  } | null>(null);
  const [previous, setPrevious] = useState<{ scope: string; preview: OrderPreview } | null>(null);
  const sequence = useRef(0);
  const previousPreview = previous?.scope === scope ? previous.preview : null;
  const reject = useRef<((error: unknown) => void) | null>(null);
  const resolve = useRef<((token: string | null) => void) | null>(null);
  const resource = useKeyedResource(
    ['order-preview', scope, job?.key],
    job?.scope === scope
      ? async () => {
          try {
            return { preview: await job.load(), error: null, cause: null };
          } catch (error) {
            return {
              preview: null,
              cause: error,
              error: getApiErrorMessage(
                error,
                '試算できませんでした。入力と権限を確認してください。'
              ),
            };
          }
        }
      : null
  );
  useEffect(
    () => () => {
      resolve.current?.(null);
      resolve.current = null;
    },
    [scope]
  );
  const close = (token: string | null) => {
    resolve.current?.(token);
    resolve.current = null;
    setJob(null);
  };
  const confirm = (load: () => Promise<OrderPreview>) =>
    new Promise<string | null>((done, fail) => {
      reject.current = fail;
      resolve.current?.(null);
      resolve.current = done;
      setJob({ load, key: ++sequence.current, scope });
    });
  const preview = resource.data?.preview;
  const dialog = (
    <Dialog
      open={job !== null && job.scope === scope}
      onOpenChange={open => {
        if (!open) close(null);
      }}
    >
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
        <DialogTitle>採用条件の確認</DialogTitle>
        {resource.isLoading ? (
          <p>試算中...</p>
        ) : resource.failure || resource.data?.error ? (
          <>
            <RegionError
              message={resource.data?.error ?? '試算を取得できませんでした'}
              onRetry={() => void resource.reload()}
            />
            {orderConflictField(resource.data?.cause) === 'expected_version' && (
              <Button
                type="button"
                onClick={() => {
                  reject.current?.(resource.data?.cause);
                  resolve.current = null;
                  reject.current = null;
                  setJob(null);
                }}
              >
                最新の受注を読み直す
              </Button>
            )}
          </>
        ) : (
          preview && (
            <div className="space-y-3">
              <p>
                {preview.course.name} / {preview.course.duration_minutes}分 / 版
                {preview.course.revision_number}
              </p>
              <p>
                コース料金: ¥{preview.course.price.toLocaleString()} / 固定報酬: ¥
                {preview.course.remuneration.toLocaleString()}
              </p>
              {preview.fee_lines.map((line, i) => (
                <p key={i}>
                  {line.name}: ¥{line.amount.toLocaleString()}
                  {line.remuneration !== undefined
                    ? ` / 固定報酬 ¥${line.remuneration.toLocaleString()}`
                    : ''}
                </p>
              ))}
              <p>請求額: ¥{preview.total_fee.toLocaleString()}</p>
              {preview.points && (
                <p>
                  {preview.points.member_linked
                    ? `会員 ${preview.points.member_code} / 利用 ${preview.points.use_points} / 付与 ${preview.points.grant_points} ポイント`
                    : '会員紐づけなし'}
                </p>
              )}
              {previousPreview &&
                previousPreview.confirmation_token !== preview.confirmation_token && (
                  <div role="status" className="rounded-lg border p-3">
                    <p>前回の確認内容から変更があります。以下を確認してください。</p>
                    <p>
                      コース: {previousPreview.course.name} → {preview.course.name} / 時間:{' '}
                      {previousPreview.course.duration_minutes} → {preview.course.duration_minutes}
                      分
                    </p>
                    <p>
                      料金: ¥{previousPreview.course.price} → ¥{preview.course.price} / 報酬: ¥
                      {previousPreview.course.remuneration} → ¥{preview.course.remuneration}
                    </p>
                    <p>
                      請求: ¥{previousPreview.total_fee} → ¥{preview.total_fee}
                    </p>
                    <p>
                      利用: {previousPreview.points?.use_points ?? 0} →{' '}
                      {preview.points?.use_points ?? 0} / 付与:{' '}
                      {previousPreview.points?.grant_points ?? 0} →{' '}
                      {preview.points?.grant_points ?? 0}
                    </p>
                  </div>
                )}
              <Button
                type="button"
                onClick={() => {
                  setPrevious({ scope, preview });
                  close(preview.confirmation_token);
                }}
              >
                この内容を確認して保存
              </Button>
            </div>
          )
        )}
        <Button type="button" variant="outline" onClick={() => close(null)}>
          入力に戻る
        </Button>
      </DialogContent>
    </Dialog>
  );
  return { confirm, dialog };
}
