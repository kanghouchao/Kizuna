'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { MEDIUM_NAME_MAX, type MediumSummary } from '@/entities/medium';
import {
  Button,
  Dialog,
  DialogFrame,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
} from '@/shared/ui';

interface MediumNameModalProps {
  open: boolean;
  /** 改名する媒体（開いた時点の写し）。null なら新規作成。 */
  medium: MediumSummary | null;
  /** 改名対象が一覧から消えた */
  gone: boolean;
  /** 改名の版を引く一覧が取れていない。一覧の再試行はダイアログの背後に隠れるので、入力を残したままここで再試行させる。 */
  listFailed: boolean;
  onRetryList: () => void;
  /** 送る版がまだ手元に無い */
  versionPending: boolean;
  busy: boolean;
  onClose: () => void;
  /** 前後の空白を除いた名前を渡す。閉じるかどうかは呼び出し側が決める。 */
  onSubmit: (name: string) => Promise<void>;
}

const NAME_REQUIRED = '媒体名を入力してください';

/** 媒体の作成・改名で共有する名前入力。前後の空白はサーバと同じく除いて数える。 */
export function MediumNameModal({
  open,
  medium,
  gone,
  listFailed,
  onRetryList,
  versionPending,
  busy,
  onClose,
  onSubmit,
}: MediumNameModalProps) {
  const form = useForm<{ name: string }>({ defaultValues: { name: '' } });
  const { control, handleSubmit, reset } = form;

  useEffect(() => {
    if (open) reset({ name: medium?.name ?? '' });
  }, [open, medium, reset]);

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <Form {...form}>
        <DialogFrame
          size="sm"
          title={medium ? '媒体名を変更' : '媒体を登録'}
          // noValidate: 文言付きの rules が required / maxLength の原生制約を置き換える
          form={
            gone
              ? undefined
              : { onSubmit: handleSubmit(({ name }) => onSubmit(name.trim())), noValidate: true }
          }
          footer={
            gone ? (
              <Button type="button" variant="outline" onClick={onClose}>
                閉じる
              </Button>
            ) : (
              <>
                <Button type="button" variant="outline" onClick={onClose}>
                  キャンセル
                </Button>
                <Button type="submit" disabled={busy || versionPending}>
                  {busy ? '保存中...' : '保存する'}
                </Button>
              </>
            )
          }
        >
          {gone ? (
            <p role="alert">この媒体は削除されています。</p>
          ) : (
            <>
              {listFailed && (
                <RegionError message="媒体一覧の取得に失敗しました" onRetry={onRetryList} />
              )}
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
            </>
          )}
        </DialogFrame>
      </Form>
    </Dialog>
  );
}
