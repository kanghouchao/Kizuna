'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { MEDIUM_NAME_MAX, mediumApi, type MediumSummary } from '@/entities/medium';
import { getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
} from '@/shared/ui';

interface MediumNameModalProps {
  open: boolean;
  /** 改名する媒体。null なら新規作成。 */
  medium: MediumSummary | null;
  onClose: () => void;
  onSaved: () => void;
}

const NAME_REQUIRED = '媒体名を入力してください';

/** 媒体の作成・改名で共有する名前入力。前後の空白はサーバと同じく除いて数える。 */
export function MediumNameModal({ open, medium, onClose, onSaved }: MediumNameModalProps) {
  const form = useForm<{ name: string }>({ defaultValues: { name: '' } });
  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;

  useEffect(() => {
    if (open) reset({ name: medium?.name ?? '' });
  }, [open, medium, reset]);

  const submit = async ({ name }: { name: string }) => {
    try {
      if (medium) await mediumApi.rename(medium.id, name.trim(), medium.version);
      else await mediumApi.create(name.trim());
      notify.success(medium ? '媒体名を変更しました' : '媒体を登録しました');
      onSaved();
      onClose();
    } catch (error) {
      notify.error(
        getApiErrorMessage(
          error,
          medium ? '媒体名の変更に失敗しました' : '媒体の登録に失敗しました'
        )
      );
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <DialogContent aria-describedby={undefined} className="sm:max-w-md">
        <DialogTitle>{medium ? '媒体名を変更' : '媒体を登録'}</DialogTitle>
        <Form {...form}>
          {/* noValidate: 文言付きの rules が required / maxLength の原生制約を置き換える */}
          <form onSubmit={handleSubmit(submit)} className="space-y-6" noValidate>
            <FormField
              control={control}
              name="name"
              rules={{
                validate: value => {
                  const trimmed = value.trim();
                  if (trimmed === '') return NAME_REQUIRED;
                  if (trimmed.length > MEDIUM_NAME_MAX)
                    return `媒体名は${MEDIUM_NAME_MAX}文字以内で入力してください`;
                  return true;
                },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>媒体名</FormLabel>
                  <FormControl>
                    <Input type="text" required autoComplete="off" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="flex justify-end gap-3">
              <Button type="button" variant="outline" onClick={onClose}>
                キャンセル
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? '保存中...' : '保存する'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
