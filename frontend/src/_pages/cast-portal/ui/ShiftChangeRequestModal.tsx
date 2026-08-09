'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { CastScheduleItem, ShiftChangeRequestCreateRequest, shiftApi } from '@/entities/shift';
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
  Textarea,
} from '@/shared/ui';
import { formatEndTime, formatTime, toDateStr } from '../lib/week';

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
  const form = useForm<ChangeFormValues>({
    defaultValues: { work_date: '', start_time: '', end_time: '', note: '' },
  });
  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;

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
      notify.success('変更申請を提出しました');
      onClose();
    } catch {
      notify.error('変更申請の提出に失敗しました');
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
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は各 rules が担う */}
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
            <p className="text-xs text-muted-foreground">
              現行: {item?.work_date} {formatTime(item?.start_time)}–{formatEndTime(item?.end_time)}
              （{item?.store_name}）
            </p>
            <FormField
              control={control}
              name="work_date"
              rules={{
                required: '希望する日付を指定してください',
                validate: v => v >= todayStr() || '本日以降の日付を指定してください',
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>日付</FormLabel>
                  <FormControl>
                    <Input type="date" required {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={control}
                name="start_time"
                rules={{ required: '開始時刻を入力してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>開始</FormLabel>
                    <FormControl>
                      <Input type="time" required {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="end_time"
                rules={{ required: '終了時刻を入力してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>終了</FormLabel>
                    <FormControl>
                      <Input type="time" required {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <FormField
              control={control}
              name="note"
              rules={{
                maxLength: { value: 500, message: '備考は500文字以内で入力してください' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>備考</FormLabel>
                  <FormControl>
                    <Textarea rows={3} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="outline" onClick={onClose}>
                キャンセル
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? '提出中...' : '変更を申請する'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
