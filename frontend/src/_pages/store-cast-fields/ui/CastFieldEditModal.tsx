'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  CastFieldDefinitionResponse,
  CastFieldDefinitionUpdateRequest,
  castFieldDefinitionApi,
} from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';
import { Button, Dialog, DialogContent, DialogTitle, Input, Label } from '@/shared/ui';

interface CastFieldEditModalProps {
  open: boolean;
  onClose: () => void;
  /** 編集対象。null なら何も表示しない。 */
  definition: CastFieldDefinitionResponse | null;
  /** 更新成功後に呼ばれる(一覧の再取得用)。 */
  onUpdated: () => void;
}

interface CastFieldEditFormValues {
  label: string;
  display_order: number;
  is_public: boolean;
}

/** カスタムフィールド定義の編集モーダル(label・表示順・公開設定のみ、key は不変のため編集不可)。 */
export function CastFieldEditModal({
  open,
  onClose,
  definition,
  onUpdated,
}: CastFieldEditModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<CastFieldEditFormValues>();

  useEffect(() => {
    if (!open || !definition) return;
    reset({
      label: definition.label,
      display_order: definition.display_order,
      is_public: definition.is_public,
    });
  }, [open, definition, reset]);

  const submit = async (values: CastFieldEditFormValues) => {
    if (!definition) return;
    try {
      const request: CastFieldDefinitionUpdateRequest = {
        label: values.label,
        display_order: values.display_order,
        is_public: values.is_public,
      };
      await castFieldDefinitionApi.update(definition.id ?? '', request);
      notify.success('フィールドを更新しました');
      onUpdated();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'フィールドの更新に失敗しました'));
    }
  };

  return (
    <Dialog
      open={open && definition !== null}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          フィールドを編集
        </DialogTitle>
        <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
          <div>
            <span className="mb-1 block text-sm font-medium text-foreground">key</span>
            <p className="text-sm text-muted-foreground">{definition?.key}</p>
          </div>
          <div className="grid gap-1">
            <Label htmlFor="field-edit-label">label</Label>
            <Input id="field-edit-label" type="text" {...register('label', { required: true })} />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="field-edit-display-order">表示順</Label>
            <Input
              id="field-edit-display-order"
              type="number"
              {...register('display_order', { valueAsNumber: true, required: true })}
            />
          </div>
          <Label className="font-normal">
            <input type="checkbox" {...register('is_public')} />
            公開する(公開詳細ページに表示)
          </Label>
          <div className="flex justify-end gap-3 border-t pt-4">
            <Button type="button" variant="outline" onClick={onClose}>
              キャンセル
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? '保存中...' : '保存する'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
