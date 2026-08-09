'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  CastFieldDefinitionResponse,
  CastFieldDefinitionUpdateRequest,
  castFieldDefinitionApi,
} from '@/entities/cast';
import { getApiErrorMessage, integerRule } from '@/shared/lib';
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
  Label,
} from '@/shared/ui';

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

const DISPLAY_ORDER_REQUIRED = '表示順を入力してください';

/** カスタムフィールド定義の編集モーダル(label・表示順・公開設定のみ、key は不変のため編集不可)。 */
export function CastFieldEditModal({
  open,
  onClose,
  definition,
  onUpdated,
}: CastFieldEditModalProps) {
  const form = useForm<CastFieldEditFormValues>();
  const {
    control,
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;

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
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は各 rules が担う */}
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
            <div>
              <span className="mb-1 block text-sm font-medium text-foreground">key</span>
              <p className="text-sm text-muted-foreground">{definition?.key}</p>
            </div>
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
            <FormField
              control={control}
              name="display_order"
              rules={{
                required: DISPLAY_ORDER_REQUIRED,
                validate: {
                  // 空欄は NaN であって null でも空文字でもないため required は素通りする。
                  // 実測で、required だけだと空欄のまま display_order: null が送信される。
                  notEmpty: value => !Number.isNaN(value) || DISPLAY_ORDER_REQUIRED,
                  // noValidate は type="number" の暗黙の step=1 も止める。これが無いと 1.5 が
                  // Integer の displayOrder へ届く
                  integer: integerRule('表示順'),
                },
              }}
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel>表示順</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      required
                      {...field}
                      // register の valueAsNumber と同じ写像。Number() は空欄を 0 にしてしまい、
                      // 「未入力」を表す NaN が失われる。
                      value={Number.isNaN(field.value) ? '' : field.value}
                      onChange={event => field.onChange(event.target.valueAsNumber)}
                    />
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
                {isSubmitting ? '保存中...' : '保存する'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
