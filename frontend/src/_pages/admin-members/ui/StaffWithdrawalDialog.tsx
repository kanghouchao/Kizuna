'use client';

import { useForm, useWatch } from 'react-hook-form';
import { adminMemberApi } from '@/entities/member';
import { getApiErrorMessage, isConflict } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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
export const CONFIRMATION_WORD = '退会';

interface FormValues {
  reason: string;
  confirmation: string;
}

interface StaffWithdrawalDialogProps {
  open: boolean;
  memberId: number;
  memberLabel: string;
  onClose: () => void;
  /** 退会が成立した後。呼び出し側が詳細を取り直す。 */
  onWithdrawn: () => void;
}

/**
 * 職員による代行退会の確認。理由を必須で受け、確認欄に「退会」と打つまで実行ボタンを開かない。
 *
 * 実行ボタンの無効化は DESIGN.md の「validation 未完了を理由に submit を disabled にしない」の例外で、取り消せない終端操作の
 * 誤操作防止として本人退会と同じ形にする。理由の必須検証は通常どおり押してから入力横に出す。
 */
export function StaffWithdrawalDialog({
  open,
  memberId,
  memberLabel,
  onClose,
  onWithdrawn,
}: StaffWithdrawalDialogProps) {
  const form = useForm<FormValues>({ defaultValues: { reason: '', confirmation: '' } });
  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;
  const confirmed = useWatch({ control, name: 'confirmation' }) === CONFIRMATION_WORD;

  const submit = async (values: FormValues) => {
    try {
      await adminMemberApi.withdraw(memberId, { reason: values.reason });
      notify.success('会員を退会させました');
      onClose();
      onWithdrawn();
    } catch (error) {
      if (isConflict(error)) {
        // 画面を開いている間に本人か別の職員が退会させた。退会済みの事実を詳細で読ませる
        notify.warning('この会員は既に退会しています。最新の内容を確認してください');
        onClose();
        onWithdrawn();
        return;
      }
      notify.error(getApiErrorMessage(error, '退会の手続きに失敗しました'));
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
      onOpenChangeComplete={next => {
        // 閉じ切ってから入力を捨てる。退出中に消すと、本文が空のまま閉じるアニメーションになる
        if (!next) reset();
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>会員を退会させますか？</DialogTitle>
          <DialogDescription>
            {memberLabel}{' '}
            のポイントはすべて消去され、店舗との紐づけは解除され、申請中の予約は取り下げられます。退会の取り消しや再入会はできません。
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          {/* noValidate: 原生制約が生きている限りブラウザが submit の手前で止め、我々の文言が描かれない。執行は rules が担う */}
          <form onSubmit={handleSubmit(submit)} noValidate className="space-y-6">
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
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>
                キャンセル
              </Button>
              <Button type="submit" variant="destructive" disabled={!confirmed || isSubmitting}>
                {isSubmitting ? '手続き中...' : '退会させる'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
