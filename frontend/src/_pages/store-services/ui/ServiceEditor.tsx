'use client';
import { useEffect, useState, useRef } from 'react';
import { serviceApi, ServiceCreateRequest } from '@/entities/service';
import {
  getApiErrorMessage,
  isBadRequest,
  isConflict,
  isForbidden,
  isNotFound,
  useResource,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  RegionError,
} from '@/shared/ui';
import { ServiceForm } from './ServiceForm';

export function ServiceEditor({
  id,
  onClose,
  onSaved,
  onForbidden,
}: {
  id: string | null;
  onClose: () => void;
  onSaved: () => void;
  onForbidden: () => void;
}) {
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
  const [conflict, setConflict] = useState(false);
  const [missing, setMissing] = useState(false);
  const [saving, setSaving] = useState(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const save = async (values: ServiceCreateRequest) => {
    setSaving(true);
    try {
      if (id && resource.data) {
        const { name, duration_minutes, charge_type, price, remuneration } = values;
        const body = { name, duration_minutes, charge_type, price, remuneration };
        await serviceApi.update(id, { ...body, expected_version: resource.data.version });
      } else {
        await serviceApi.create(values);
      }
      if (!active.current) return;
      notify.success('サービスを保存しました');
      onSaved();
      onClose();
    } catch (error) {
      if (!active.current) return;
      if (isConflict(error)) setConflict(true);
      else if (isNotFound(error)) setMissing(true);
      else if (isForbidden(error)) onForbidden();
      else {
        notify.error(getApiErrorMessage(error, 'サービスの保存に失敗しました'));
        if (id && isBadRequest(error)) await resource.reload();
      }
    } finally {
      if (active.current) setSaving(false);
    }
  };
  const unavailable = missing || resource.failure === 'notFound' || resource.data?.deleted;
  return (
    <Dialog
      open
      onOpenChange={open => {
        if (!open && !saving) {
          onClose();
          if (unavailable) onSaved();
        }
      }}
    >
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{id ? 'サービス編集' : 'サービス作成'}</DialogTitle>
          <DialogDescription>保存した条件は即時に適用されます。</DialogDescription>
        </DialogHeader>
        {unavailable ? (
          <div role="alert">
            サービスが存在しないか削除されています。
            <Button
              variant="outline"
              onClick={() => {
                onClose();
                onSaved();
              }}
            >
              閉じる
            </Button>
          </div>
        ) : resource.failure !== null ? (
          <RegionError
            message="サービスの取得に失敗しました"
            onRetry={() => void resource.reload()}
          />
        ) : resource.isLoading || (id && !resource.data) ? (
          <p>読み込み中...</p>
        ) : (
          <>
            {conflict && (
              <div role="alert" className="space-y-3">
                <p>設定が変更されています。最新の内容を再取得し、確認してから保存してください。</p>
                <Button
                  variant="outline"
                  onClick={async () => {
                    await resource.reload();
                    if (active.current) setConflict(false);
                  }}
                >
                  最新の内容を再取得
                </Button>
              </div>
            )}
            <ServiceForm
              key={resource.data?.version ?? 'new'}
              initial={resource.data ?? undefined}
              onSave={save}
              disabled={saving || conflict}
            />
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
