'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { CastFieldDefinitionCreateRequest, castFieldDefinitionApi } from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';
import { Button, Dialog, DialogContent, DialogTitle, Input, Label } from '@/shared/ui';

interface CastFieldCreateModalProps {
  open: boolean;
  onClose: () => void;
  /** 作成成功後に呼ばれる(一覧の再取得用)。 */
  onCreated: () => void;
}

interface CastFieldCreateFormValues {
  key: string;
  label: string;
  is_public: boolean;
}

/** カスタムフィールド定義の新規作成モーダル(key・label・公開設定)。 */
export function CastFieldCreateModal({ open, onClose, onCreated }: CastFieldCreateModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<CastFieldCreateFormValues>();

  useEffect(() => {
    if (!open) return;
    reset({ key: '', label: '', is_public: false });
  }, [open, reset]);

  const submit = async (values: CastFieldCreateFormValues) => {
    try {
      const request: CastFieldDefinitionCreateRequest = {
        key: values.key,
        label: values.label,
        is_public: values.is_public,
      };
      await castFieldDefinitionApi.create(request);
      notify.success('フィールドを追加しました');
      onCreated();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'フィールドの追加に失敗しました'));
    }
  };

  return (
    <Dialog
      open={open}
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
          フィールドを追加
        </DialogTitle>
        <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
          <div className="grid gap-1">
            <Label htmlFor="field-key">key</Label>
            <Input
              id="field-key"
              type="text"
              {...register('key', {
                required: true,
                // バックエンドの @Pattern と同期。constructor・prototype は
                // react-hook-form の register 内部予約名で、定義化するとキャスト
                // 編集フォームの描画をクラッシュさせるため作成時に拒否する。
                pattern: /^(?!constructor$|prototype$)[a-z][a-z0-9_]*$/,
              })}
            />
            <p className="text-xs text-muted-foreground">
              英小文字で始まり、英小文字・数字・アンダースコアのみ使用できます(作成後は変更できません)
            </p>
          </div>
          <div className="grid gap-1">
            <Label htmlFor="field-label">label</Label>
            <Input id="field-label" type="text" {...register('label', { required: true })} />
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
              {isSubmitting ? '追加中...' : '追加する'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
