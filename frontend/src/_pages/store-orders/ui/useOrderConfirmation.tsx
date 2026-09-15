'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { isDeduction, OrderPreview, OrderSpecialService } from '@/entities/order';
import { getApiErrorMessage, isConflict, useKeyedResource } from '@/shared/lib';
import { Button, Dialog, DialogContent, DialogTitle, RegionError } from '@/shared/ui';

export function orderConflictField(error: unknown): string | undefined {
  if (!isConflict(error)) return undefined;
  const details = (error as { response?: { data?: { details?: Record<string, unknown> } } })
    .response?.data?.details;
  if (details?.confirmation_token) return 'confirmation_token';
  if (details?.special_services) return 'special_services';
  if (details?.expected_version) return 'expected_version';
  return undefined;
}

function specialServiceComparison(item: OrderSpecialService) {
  const status = item.requires_attention
    ? '本人拒否・要対応'
    : item.current_consent_status === 'ACCEPTED'
      ? '受諾済み'
      : item.current_consent_status === 'REJECTED'
        ? '拒否'
        : item.current_consent_status === 'RECONFIRMATION_REQUIRED'
          ? '再受諾待ち'
          : item.adoption_basis === 'HISTORICAL_CORRECTION'
            ? '履歴訂正'
            : item.current_consent_status == null && item.adoption_basis === 'ACCEPTED_TERMS'
              ? '受諾時の約定'
              : '未受諾';
  return `${item.name} 版${item.revision_number} 料金¥${item.price} 報酬¥${item.remuneration} 担当在籍 ${item.enrollment_id} / 受諾状態 ${status} / 受諾版 ${item.consent_version ?? 'なし'}`;
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
                  {line.name}: {isDeduction(line.kind) ? '-' : ''}¥{line.amount.toLocaleString()}
                  {line.duration_minutes !== undefined ? ` / ${line.duration_minutes}分` : ''}
                  {line.revision_number !== undefined ? ` / 版${line.revision_number}` : ''}
                  {line.remuneration !== undefined
                    ? ` / 固定報酬 ¥${line.remuneration.toLocaleString()}`
                    : ''}
                </p>
              ))}
              <p>
                総時間: {preview.total_duration_minutes}分 / 固定報酬合計: ¥
                {preview.total_remuneration.toLocaleString()}
              </p>
              {preview.requires_attention && (
                <p role="alert">拒否項目が残っています。保存後も開始・完了には修復が必要です。</p>
              )}
              {(preview.special_services ?? []).map(item => (
                <p key={item.service_id}>
                  {item.name} / 版{item.revision_number} /{' '}
                  {item.requires_attention
                    ? '本人拒否・要対応'
                    : item.current_consent_status === 'RECONFIRMATION_REQUIRED'
                      ? '再受諾待ち（旧約定を保持）'
                      : '採用可能'}
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
                (previousPreview.confirmation_token !== preview.confirmation_token ||
                  JSON.stringify(previousPreview.special_services.map(specialServiceComparison)) !==
                    JSON.stringify(preview.special_services.map(specialServiceComparison))) && (
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
                      総時間: {previousPreview.total_duration_minutes} →{' '}
                      {preview.total_duration_minutes}分 / 固定報酬合計: ¥
                      {previousPreview.total_remuneration} → ¥{preview.total_remuneration}
                    </p>
                    {previousPreview.fee_lines.map((before, index) => {
                      const after = preview.fee_lines[index];
                      return (
                        <p key={index}>
                          明細{index + 1}: {before.name} / 版{before.revision_number ?? '—'} / 料金
                          ¥{before.amount} / 報酬 ¥{before.remuneration}
                          {' → '}
                          {after
                            ? `${after.name} / 版${after.revision_number ?? '—'} / 料金 ¥${after.amount} / 報酬 ¥${after.remuneration}`
                            : '除去'}
                        </p>
                      );
                    })}
                    {preview.fee_lines
                      .slice(previousPreview.fee_lines.length)
                      .map((line, index) => (
                        <p key={index}>
                          追加: {line.name} / 版{line.revision_number ?? '—'} / 料金 ¥{line.amount}{' '}
                          / 報酬 ¥{line.remuneration}
                        </p>
                      ))}
                    <p>
                      特殊サービス（前回）:{' '}
                      {(previousPreview.special_services ?? [])
                        .map(specialServiceComparison)
                        .join('、') || 'なし'}
                    </p>
                    <p>
                      特殊サービス（今回）:{' '}
                      {(preview.special_services ?? []).map(specialServiceComparison).join('、') ||
                        'なし'}
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
