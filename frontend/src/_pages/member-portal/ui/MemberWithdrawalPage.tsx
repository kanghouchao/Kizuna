'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useForm, useWatch } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { memberApi } from '@/entities/member';
import { useAuth } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Textarea,
} from '@/shared/ui';

/** 実行ボタンを開くために確認欄へ入力してもらう文字。送信はしない。 */
const CONFIRMATION_WORD = '退会';

interface WithdrawalFormValues {
  reason: string;
  confirmation: string;
}

/**
 * 会員ポータルの退会画面。退会で起きることを先に読ませ、理由を必須で受け、確認欄に「退会」と打つまで実行ボタンを開かない。
 *
 * 実行ボタンの無効化は DESIGN.md の「validation 未完了を理由に submit を disabled にしない」の例外で、取り消せない終端操作の
 * 誤操作防止として issue が定めた形。理由の必須検証は通常どおり押してから入力横に出す。
 * 成功後は旧トークンが失効しているため、通知は出さずログアウトしてログイン画面に理由コードで名乗らせる。
 */
export function MemberWithdrawalPage() {
  const { logout } = useAuth();
  const form = useForm<WithdrawalFormValues>({
    defaultValues: { reason: '', confirmation: '' },
  });
  const {
    control,
    handleSubmit,
    formState: { isSubmitting },
  } = form;
  // 成功後はログアウトの遷移が進行中でこの画面はまだ立っている。isSubmitting だけでは遷移の間だけ再び押せてしまう。
  const [withdrawn, setWithdrawn] = useState(false);
  const confirmed = useWatch({ control, name: 'confirmation' }) === CONFIRMATION_WORD;
  const pending = isSubmitting || withdrawn;

  const submit = async (values: WithdrawalFormValues) => {
    try {
      await memberApi.withdraw({ reason: values.reason });
      setWithdrawn(true);
      logout('member-withdrawn');
    } catch (error) {
      notify.error(getApiErrorMessage(error, '退会の手続きに失敗しました'));
    }
  };

  return (
    <div className="mx-auto w-full max-w-md p-4">
      <h1 className="mt-2 text-lg font-semibold text-foreground">退会</h1>
      <Card className="mt-4">
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            退会すると
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="list-disc space-y-2 pl-5 text-sm text-foreground">
            <li>保有しているポイントはすべて消去され、元に戻せません。</li>
            <li>店舗との紐づけは解除され、申請中の予約は取り下げられます。</li>
            <li>退会の取り消しや再入会はできません。</li>
            <li>
              同じメールアドレスで再び登録できますが、新しい会員として始まります（会員コード・ポイント・ランクは引き継がれません）。
            </li>
          </ul>
        </CardContent>
      </Card>
      <Form {...form}>
        {/* noValidate: 原生制約が生きている限りブラウザが submit の手前で止め、我々の文言が描かれない。執行は rules が担う */}
        <form onSubmit={handleSubmit(submit)} noValidate>
          <Card className="mt-4">
            <CardHeader>
              <CardTitle role="heading" aria-level={2}>
                退会の手続き
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <FormField
                control={control}
                name="reason"
                rules={{
                  required: '退会理由を入力してください',
                  maxLength: { value: 500, message: '退会理由は500文字以内で入力してください' },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>退会理由</FormLabel>
                    <FormControl>
                      <Textarea rows={3} required {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="confirmation"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>確認のため「{CONFIRMATION_WORD}」と入力</FormLabel>
                    <FormControl>
                      <Input autoComplete="off" {...field} />
                    </FormControl>
                  </FormItem>
                )}
              />
              <Button
                type="submit"
                variant="destructive"
                className="w-full"
                disabled={!confirmed || pending}
              >
                {pending ? '手続き中...' : '退会する'}
              </Button>
            </CardContent>
          </Card>
        </form>
      </Form>
      <Button render={<Link href="/member/" />} variant="outline" className="mt-6 w-full">
        ホームへ戻る
      </Button>
    </div>
  );
}
