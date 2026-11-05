'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { useStoreContext } from '@/entities/user';
import { OrderPreview } from '@/entities/order';
import { getApiErrorMessage, isConflict, useKeyedResource } from '@/shared/lib';
import { Button, Dialog, DialogFrame, RegionError } from '@/shared/ui';
import { OrderPreviewDetails } from './OrderPreviewDetails';

export function orderConflictField(error: unknown): string | undefined {
  if (!isConflict(error)) return undefined;
  const details = (error as { response?: { data?: { details?: Record<string, unknown> } } })
    .response?.data?.details;
  if (details?.customer_lock) return 'customer_lock';
  if (details?.use_points) return 'use_points';
  if (details?.confirmation_token) return 'confirmation_token';
  if (details?.special_services) return 'special_services';
  if (details?.expected_version) return 'expected_version';
  return undefined;
}

export function useOrderConfirmation(target?: string) {
  const params = useParams();
  const storeId = useStoreContext().currentStoreId;
  const scope = `${storeId}:${target ?? params?.id ?? ''}`;
  const [open, setOpen] = useState(false);
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
    setOpen(false);
  };
  const confirm = (load: () => Promise<OrderPreview>) =>
    new Promise<string | null>((done, fail) => {
      reject.current = fail;
      resolve.current?.(null);
      resolve.current = done;
      setJob({ load, key: ++sequence.current, scope });
      setOpen(true);
    });
  const preview = resource.data?.preview;
  const failed = !resource.isLoading && (resource.failure || resource.data?.error);
  const reloadable = failed && orderConflictField(resource.data?.cause) === 'expected_version';
  const confirmable = !resource.isLoading && !failed && preview;
  const dialog = (
    <Dialog
      open={open && job?.scope === scope}
      onOpenChange={open => {
        if (!open) close(null);
      }}
    >
      <DialogFrame
        size="md"
        title="採用条件の確認"
        description="保存する前に、料金・報酬・ポイントの採用内容を確認してください。"
        footer={
          <>
            <Button type="button" variant="outline" onClick={() => close(null)}>
              入力に戻る
            </Button>
            {reloadable && (
              <Button
                type="button"
                onClick={() => {
                  reject.current?.(resource.data?.cause);
                  resolve.current = null;
                  reject.current = null;
                  setOpen(false);
                }}
              >
                最新の受注を読み直す
              </Button>
            )}
            {confirmable && (
              <Button
                type="button"
                onClick={() => {
                  setPrevious({ scope, preview });
                  close(preview.confirmation_token);
                }}
              >
                この内容を確認して保存
              </Button>
            )}
          </>
        }
      >
        {resource.isLoading ? (
          <p>試算中...</p>
        ) : failed ? (
          <RegionError
            message={resource.data?.error ?? '試算を取得できませんでした'}
            onRetry={() => void resource.reload()}
          />
        ) : (
          preview && <OrderPreviewDetails preview={preview} previous={previousPreview} />
        )}
      </DialogFrame>
    </Dialog>
  );
  return { confirm, dialog };
}
