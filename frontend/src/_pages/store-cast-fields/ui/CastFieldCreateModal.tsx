'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { CastFieldDefinitionCreateRequest, castFieldDefinitionApi } from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Label,
} from '@/shared/ui';

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
  const form = useForm<CastFieldCreateFormValues>();
  const {
    control,
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;

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
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は各 rules が担う */}
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
            <FormField
              control={control}
              name="key"
              rules={{
                required: 'key を入力してください',
                // バックエンドの @Pattern と同期。constructor・prototype は
                // react-hook-form の register 内部予約名で、定義化するとキャスト
                // 編集フォームの描画をクラッシュさせるため作成時に拒否する。
                pattern: {
                  value: /^(?!constructor$|prototype$)[a-z][a-z0-9_]*$/,
                  message:
                    'key は英小文字で始まり、英小文字・数字・アンダースコアのみ使用できます(constructor・prototype は使えません)',
                },
              }}
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel>key</FormLabel>
                  <FormControl>
                    <Input type="text" required {...field} />
                  </FormControl>
                  <FormDescription className="text-xs">
                    英小文字で始まり、英小文字・数字・アンダースコアのみ使用できます(作成後は変更できません)
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="label"
              rules={{ required: 'label を入力してください' }}
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel>label</FormLabel>
                  <FormControl>
                    <Input type="text" required {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
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
        </Form>
      </DialogContent>
    </Dialog>
  );
}
