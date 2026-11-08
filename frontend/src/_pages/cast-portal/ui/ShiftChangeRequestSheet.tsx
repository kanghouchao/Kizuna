'use client';

import { useEffect, useId } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { formatDate, formatTimeRange, getApiErrorMessage, tokyoDate } from '@/shared/lib';
import { CastScheduleItem, ShiftChangeRequestCreateRequest, shiftApi } from '@/entities/shift';
import {
  BottomSheet,
  BottomSheetClose,
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Textarea,
} from '@/shared/ui';

interface ChangeFormValues {
  work_date: string;
  start_time: string;
  end_time: string;
  note: string;
}

interface ShiftChangeRequestSheetProps {
  open: boolean;
  item: CastScheduleItem | null;
  onClose: () => void;
}

function currentLabel(item: CastScheduleItem | null): string | undefined {
  if (!item?.work_date) return undefined;
  const date = formatDate(item.work_date, tokyoDate(new Date()));
  const range = formatTimeRange(item.start_time ?? '', item.end_time ?? '');
  return `現在: ${date}${range}・${item.store_name ?? ''}`;
}

/** 確定シフトへの変更申請の下部シート。現行の日時を初期値に、希望する日時・備考を提出する。 */
export function ShiftChangeRequestSheet({ open, item, onClose }: ShiftChangeRequestSheetProps) {
  const formId = useId();
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
    if (!open || !item) return;
    reset({
      work_date: item.work_date ?? '',
      start_time: (item.start_time ?? '').slice(0, 5),
      end_time: (item.end_time ?? '').slice(0, 5),
      note: '',
    });
  }, [open, item, reset]);

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
    } catch (e) {
      // 受理できる日付の下限は営業日で決まり、その境界（日付変更時刻・業務のタイムゾーン）を知るのは
      // サーバだけ。client 側で暦日から下限を組むと深夜帯や時差でサーバと食い違うため、日付の可否は
      // サーバの文言をそのまま見せる。
      notify.error(getApiErrorMessage(e, '変更申請の提出に失敗しました'));
    }
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
      }}
      title="シフトの変更申請"
      description={currentLabel(item)}
      footer={
        <>
          {/* 操作帯は form の外に描かれるため、form 属性で本文の form を送信する */}
          <Button type="submit" form={formId} disabled={isSubmitting}>
            {isSubmitting ? '提出中...' : '変更を申請する'}
          </Button>
          <BottomSheetClose render={<Button type="button" variant="outline" />}>
            やめる
          </BottomSheetClose>
        </>
      }
    >
      <Form {...form}>
        {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
            我々の文言は永久に描かれない。執行は各 rules が担う */}
        <form id={formId} onSubmit={handleSubmit(submit)} noValidate className="space-y-4">
          <FormField
            control={control}
            name="work_date"
            rules={{ required: '希望する日付を指定してください' }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>日付</FormLabel>
                <FormControl>
                  <Input type="date" required className="h-11" {...field} />
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
                    <Input type="time" required className="h-11" {...field} />
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
                    <Input type="time" required className="h-11" {...field} />
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
        </form>
      </Form>
    </BottomSheet>
  );
}
