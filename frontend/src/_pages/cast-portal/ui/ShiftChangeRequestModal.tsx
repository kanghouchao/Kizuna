'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { CastScheduleItem, ShiftChangeRequestCreateRequest, shiftApi } from '@/entities/shift';
import { Button, Dialog, DialogContent, DialogTitle, Input, Label, Textarea } from '@/shared/ui';
import { toDateStr } from '../lib/week';

interface ChangeFormValues {
  work_date: string;
  start_time: string;
  end_time: string;
  note: string;
}

interface ShiftChangeRequestModalProps {
  /** 変更申請の対象（確定シフト）。null なら閉じている。 */
  item: CastScheduleItem | null;
  onClose: () => void;
}

/** 本日の 'yyyy-MM-dd' を返す（過去日拒否の下限）。 */
function todayStr(): string {
  return toDateStr(new Date());
}

/** 確定シフトへの変更申請モーダル。現行の日時を初期値に、希望する日時・備考を提出する。 */
export function ShiftChangeRequestModal({ item, onClose }: ShiftChangeRequestModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangeFormValues>({
    defaultValues: { work_date: '', start_time: '', end_time: '', note: '' },
  });

  useEffect(() => {
    if (!item) return;
    reset({
      work_date: item.work_date ?? '',
      start_time: (item.start_time ?? '').slice(0, 5),
      end_time: (item.end_time ?? '').slice(0, 5),
      note: '',
    });
  }, [item, reset]);

  const submit = async (values: ChangeFormValues) => {
    if (!item?.id) return;
    const payload: ShiftChangeRequestCreateRequest = {
      shift_id: item.id,
      work_date: values.work_date,
      start_time: `${values.start_time}:00`,
      end_time: `${values.end_time}:00`,
      note: values.note || undefined,
    };
    try {
      await shiftApi.submitShiftChangeRequest(payload);
      toast.success('変更申請を提出しました');
      onClose();
    } catch {
      toast.error('変更申請の提出に失敗しました');
    }
  };

  return (
    <Dialog
      open={item !== null}
      onOpenChange={next => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="gap-0 rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4">シフトの変更申請</DialogTitle>
        <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
          <p className="text-xs text-muted-foreground">
            現行: {item?.work_date} {(item?.start_time ?? '').slice(0, 5)}–
            {(item?.end_time ?? '').slice(0, 5)}（{item?.store_name}）
          </p>
          <div className="grid gap-2">
            <Label htmlFor="change-work-date">日付</Label>
            <Input
              id="change-work-date"
              type="date"
              {...register('work_date', {
                required: true,
                validate: v => v >= todayStr() || '本日以降の日付を指定してください',
              })}
            />
            {errors.work_date && (
              <p className="text-xs text-destructive-strong">{errors.work_date.message}</p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="change-start-time">開始</Label>
              <Input
                id="change-start-time"
                type="time"
                {...register('start_time', { required: true })}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="change-end-time">終了</Label>
              <Input
                id="change-end-time"
                type="time"
                {...register('end_time', { required: true })}
              />
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="change-note">備考</Label>
            <Textarea
              id="change-note"
              {...register('note', {
                maxLength: { value: 500, message: '備考は500文字以内で入力してください' },
              })}
              rows={3}
            />
            {errors.note && (
              <p className="text-xs text-destructive-strong">{errors.note.message}</p>
            )}
          </div>
          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="outline" onClick={onClose}>
              キャンセル
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? '提出中...' : '変更を申請する'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
