'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { authApi, useAuth } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
} from '@/shared/ui';

interface PasswordChangeFormValues {
  current_password: string;
  new_password: string;
  confirm_password: string;
}

const NOTICE = '変更後は自動的にログアウトされ、新しいパスワードでの再ログインが必要です。';

/**
 * パスワード変更フォーム。成功するとトークンが失効するため、ログアウトして再ログインを促す。
 * console は管理画面のカード（章見出し付き）、portal は本人ポータルの下位画面用で、
 * 画面名を題名帯の h1 に任せ、1 列・押せる領域 44px で描く。
 */
export function PasswordChangeForm({ layout = 'console' }: { layout?: 'console' | 'portal' }) {
  const { logout } = useAuth();
  const form = useForm<PasswordChangeFormValues>({
    defaultValues: { current_password: '', new_password: '', confirm_password: '' },
  });
  const {
    control,
    handleSubmit,
    formState: { isSubmitting },
  } = form;
  // 成功後はログアウトの遷移が進行中で、この画面はまだ立っている。isSubmitting は
  // ハンドラの完了で戻るため、それだけを見ると遷移の間だけボタンが再び押せてしまう。
  // react-hook-form の isSubmitSuccessful は使えない — ハンドラが自分で握り潰した
  // 失敗も「成功」と数えるため、失敗後もボタンが塞がったままになる。
  const [changed, setChanged] = useState(false);

  const pending = isSubmitting || changed;

  const submit = async (values: PasswordChangeFormValues) => {
    try {
      await authApi.changePassword({
        current_password: values.current_password,
        new_password: values.new_password,
      });
      setChanged(true);
      // 成功の知らせは出さない。直後のログアウトでこの画面ごと破棄され、通知は読まれる前に消える。
      // 代わりに理由コードを渡し、着地したログイン画面に名乗らせる。
      logout('password-changed');
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'パスワードの変更に失敗しました'));
    }
  };

  const portal = layout === 'portal';
  const inputClass = portal ? 'h-11 md:text-base' : undefined;
  const fields = (
    <>
      <FormField
        control={control}
        name="current_password"
        rules={{ required: '現在のパスワードを入力してください' }}
        render={({ field }) => (
          <FormItem>
            <FormLabel>現在のパスワード *</FormLabel>
            <FormControl>
              <Input
                type="password"
                autoComplete="current-password"
                required
                className={inputClass}
                {...field}
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
      <FormField
        control={control}
        name="new_password"
        rules={{
          required: '新しいパスワードを入力してください',
          minLength: { value: 8, message: '新しいパスワードは8文字以上で入力してください' },
        }}
        render={({ field }) => (
          <FormItem>
            <FormLabel>新しいパスワード（8文字以上）*</FormLabel>
            <FormControl>
              <Input
                type="password"
                autoComplete="new-password"
                required
                className={inputClass}
                {...field}
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
      <FormField
        control={control}
        name="confirm_password"
        rules={{
          required: '確認用のパスワードを入力してください',
          validate: (value, values) =>
            value === values.new_password || '新しいパスワードが一致しません',
        }}
        render={({ field }) => (
          <FormItem>
            <FormLabel>新しいパスワード（確認）*</FormLabel>
            <FormControl>
              <Input
                type="password"
                autoComplete="new-password"
                required
                className={inputClass}
                {...field}
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
    </>
  );
  const submitLabel = pending ? '変更中...' : 'パスワードを変更する';

  return (
    <Form {...form}>
      {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
          我々の文言は永久に描かれない。執行は下の各 rules が担う */}
      <form onSubmit={handleSubmit(submit)} noValidate>
        {portal ? (
          <div className="space-y-4 rounded-xl border bg-card p-4 text-card-foreground">
            <p className="text-sm text-muted-foreground">{NOTICE}</p>
            {fields}
            <Button type="submit" disabled={pending} className="min-h-11 w-full text-base">
              {submitLabel}
            </Button>
          </div>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle role="heading" aria-level={2}>
                パスワード変更
              </CardTitle>
              <CardDescription>{NOTICE}</CardDescription>
            </CardHeader>
            <CardContent className="grid grid-cols-1 md:grid-cols-3 gap-6">{fields}</CardContent>
            <CardFooter className="justify-end">
              <Button type="submit" disabled={pending}>
                {submitLabel}
              </Button>
            </CardFooter>
          </Card>
        )}
      </form>
    </Form>
  );
}
