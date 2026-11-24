'use client';
import { useState } from 'react';
import { serviceApi, ServiceCreateRequest } from '@/entities/service';
import {
  getApiErrorMessage,
  isBadRequest,
  isForbidden,
  useResource,
  useVersionedWrite,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Button, Dialog, DialogFrame, RegionError, VersionConflictNotice } from '@/shared/ui';
import { ServiceForm } from './ServiceForm';

export function ServiceEditor({
  id,
  onClose: handleClosed,
  onSaved,
  onForbidden,
}: {
  id: string | null;
  onClose: () => void;
  onSaved: () => void;
  onForbidden: () => void;
}) {
  const [open, setOpen] = useState(true);
  const onClose = () => setOpen(false);
  const resource = useResource(
    id
      ? async () => {
          try {
            return await serviceApi.get(id);
          } catch (error) {
            if (isForbidden(error)) onForbidden();
            throw error;
          }
        }
      : null,
    [id]
  );
  const writer = useVersionedWrite(resource.reload);
  // 書き込みが 404 を返したら、取り直しの結果を待たずに「存在しない」へ替える
  const [missing, setMissing] = useState(false);
  const save = async (values: ServiceCreateRequest) => {
    const current = resource.data;
    const send = () => {
      if (!id || !current) return serviceApi.create(values);
      const { name, duration_minutes, charge_type, price, remuneration } = values;
      const body = { name, duration_minutes, charge_type, price, remuneration };
      return serviceApi.update(id, { ...body, expected_version: current.version });
    };
    const outcome = await writer.write(send, {
      success: 'サービスを保存しました',
      // 競合・消失・権限なしは画面の表示で伝えるので、案内はそれ以外の失敗にだけ出す。
      // 新規作成の競合は画面に案内を出さないので、ここで伝える
      failure: (error, kind) => {
        if (kind === 'failed' || (kind === 'conflict' && !id))
          notify.error(getApiErrorMessage(error, 'サービスの保存に失敗しました'));
      },
      onSuccess: () => {
        onSaved();
        onClose();
      },
      onFailure: (error, kind) => {
        if (kind === 'forbidden') onForbidden();
        else if (kind === 'notFound') setMissing(true);
        else if (kind === 'failed' && id && isBadRequest(error)) return writer.reload();
      },
    });
    // 新規作成には読み込み直す対象が無いので、競合をその場で畳んで入力のまま送り直せるようにする
    if (outcome === 'conflict' && !id) await writer.reload();
  };
  const { busy, conflict } = writer;
  const unavailable = missing || resource.failure === 'notFound' || resource.data?.deleted;
  return (
    <Dialog
      open={open}
      onOpenChangeComplete={next => {
        if (!next) handleClosed();
      }}
      onOpenChange={open => {
        if (!open && !busy) {
          onClose();
          if (unavailable) onSaved();
        }
      }}
    >
      <DialogFrame
        size="sm"
        title={id ? 'サービス編集' : 'サービス作成'}
        description="保存した条件は即時に適用されます。"
        footer={
          unavailable && (
            <Button
              variant="outline"
              onClick={() => {
                onClose();
                onSaved();
              }}
            >
              閉じる
            </Button>
          )
        }
      >
        {unavailable ? (
          <div role="alert">サービスが存在しないか削除されています。</div>
        ) : resource.failure !== null ? (
          <RegionError
            message="サービスの取得に失敗しました"
            onRetry={() => void writer.reload()}
          />
        ) : resource.isLoading || (id && !resource.data) ? (
          <p>読み込み中...</p>
        ) : (
          <>
            <ServiceForm
              key={resource.data?.version ?? 'new'}
              initial={resource.data ?? undefined}
              onSave={save}
              disabled={busy || conflict}
            />
            {conflict && id && (
              <VersionConflictNotice busy={busy} onLoad={() => void writer.reload()} />
            )}
          </>
        )}
      </DialogFrame>
    </Dialog>
  );
}
