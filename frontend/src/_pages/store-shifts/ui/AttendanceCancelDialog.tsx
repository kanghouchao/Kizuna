'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { AttendanceResponse, attendanceApi } from '@/entities/shift';
import { getApiErrorMessage } from '@/shared/lib';
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
  Textarea,
} from '@/shared/ui';

interface AttendanceCancelDialogProps {
  open: boolean;
  onClose: () => void;
  /** 取消の対象。open が false の間は null。 */
  attendance: AttendanceResponse | null;
  /** 取消の成功後に呼ばれる（実績・欠勤の再取得用）。 */
  onCancelled: () => void;
}

interface CancelFormValues {
  reason: string;
}

/**
 * 実績の取消。理由の欄があるため確認ダイアログ（ConfirmDialog）では受けられない。行は消えず
 * 導出・照会から外れるだけで（法定保存 — ADR 0014）、経緯を辿れる根拠は理由の一文しか残らない。
 */
export function AttendanceCancelDialog({
  open,
  onClose,
  attendance,
  onCancelled,
}: AttendanceCancelDialogProps) {
  const form = useForm<CancelFormValues>({ defaultValues: { reason: '' } });
  const {
    handleSubmit,
    reset,
    control,
    formState: { isSubmitting },
  } = form;

  useEffect(() => {
    if (open) reset({ reason: '' });
  }, [open, reset]);

  const submit = async (values: CancelFormValues) => {
    if (attendance === null) return;
    try {
      await attendanceApi.cancel(attendance.id ?? '', values.reason);
      notify.success('当日実績を取り消しました');
      onCancelled();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '当日実績の取消に失敗しました'));
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
        <DialogTitle className="border-b px-6 py-4">この実績を取り消しますか？</DialogTitle>
        <Form {...form}>
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
            <p className="text-sm text-muted-foreground">
              記録そのものは残り、集計と欠勤の導出から外れます。取り消したあとは記録し直せます。
            </p>
            <FormField
              control={control}
              name="reason"
              rules={{
                required: '取消の理由を入力してください',
                maxLength: { value: 500, message: '取消の理由は 500 文字以内で入力してください' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>取消の理由</FormLabel>
                  <FormControl>
                    <Textarea rows={3} required {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="flex justify-end gap-3 border-t pt-4">
              <Button type="button" variant="outline" onClick={onClose}>
                キャンセル
              </Button>
              <Button type="submit" variant="destructive" disabled={isSubmitting}>
                {isSubmitting ? '取消中...' : '取り消す'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
