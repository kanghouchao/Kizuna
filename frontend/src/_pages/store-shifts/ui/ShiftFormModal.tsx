'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { CastResponse } from '@/entities/cast';
import { ShiftResponse, shiftApi } from '@/entities/shift';
import { getApiErrorMessage } from '@/shared/lib';
import {
  Button,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';
import { toDateStr } from '../lib/datetime';

interface ShiftFormValues {
  cast_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  status: string;
  public_visible: boolean;
}

interface ShiftFormModalProps {
  open: boolean;
  onClose: () => void;
  casts: CastResponse[];
  /** 編集対象。null なら新規作成。 */
  editing: ShiftResponse | null;
  /** 新規作成時の初期日付 'yyyy-MM-dd'。 */
  defaultDate: string;
  /** 保存・削除の成功後に呼ばれる（一覧の再取得用）。 */
  onSaved: () => void;
}

const STATUS_OPTIONS = [
  { value: 'TENTATIVE', label: '未確定' },
  { value: 'CONFIRMED', label: '確定' },
];

// Radix Select は value="" を許容しないため、キャスト未登録の案内項目に与える番兵値。
// onValueChange で '' に戻すことで、フォームが持つ値は従来どおり空文字になる。
const SELECT_NONE = '__none__';

/** シフトの追加・編集モーダル。 */
export function ShiftFormModal({
  open,
  onClose,
  casts,
  editing,
  defaultDate,
  onSaved,
}: ShiftFormModalProps) {
  const form = useForm<ShiftFormValues>({
    defaultValues: {
      cast_id: '',
      work_date: '',
      start_time: '',
      end_time: '',
      status: 'TENTATIVE',
      public_visible: true,
    },
  });
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { isSubmitting },
  } = form;
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [actualStartTime, setActualStartTime] = useState('');
  const [actualEndTime, setActualEndTime] = useState('');
  const [recordingActual, setRecordingActual] = useState(false);
  const actualUnavailableMessage =
    editing?.status !== 'CONFIRMED'
      ? '未確定のシフトには実績を記録できません'
      : (editing.work_date ?? '') > toDateStr(new Date())
        ? '未来のシフトには実績を記録できません'
        : null;

  useEffect(() => {
    if (!open) return;
    if (editing) {
      reset({
        cast_id: editing.cast_id ?? '',
        work_date: editing.work_date ?? '',
        start_time: (editing.start_time ?? '').slice(0, 5),
        end_time: (editing.end_time ?? '').slice(0, 5),
        status: editing.status ?? '',
        public_visible: editing.public_visible ?? true,
      });
      setActualStartTime((editing.actual_start_time ?? '').slice(0, 5));
      setActualEndTime((editing.actual_end_time ?? '').slice(0, 5));
    } else {
      reset({
        cast_id: casts[0]?.id ?? '',
        work_date: defaultDate,
        start_time: '18:00',
        end_time: '23:00',
        status: 'TENTATIVE',
        public_visible: true,
      });
    }
  }, [open, editing, defaultDate, casts, reset]);

  const submit = async (values: ShiftFormValues) => {
    const payload = {
      cast_id: values.cast_id,
      work_date: values.work_date,
      start_time: `${values.start_time}:00`,
      end_time: `${values.end_time}:00`,
      status: values.status,
      public_visible: values.public_visible,
    };
    try {
      if (editing) {
        await shiftApi.update(editing.id ?? '', payload);
        toast.success('シフトを更新しました');
      } else {
        await shiftApi.create(payload);
        toast.success('シフトを追加しました');
      }
      onSaved();
      onClose();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'シフトの保存に失敗しました'));
    }
  };

  const recordActual = async () => {
    if (!editing || !actualStartTime || !actualEndTime) return;
    setRecordingActual(true);
    try {
      await shiftApi.recordActual(editing.id ?? '', {
        start_time: `${actualStartTime}:00`,
        end_time: `${actualEndTime}:00`,
      });
      toast.success('当日実績を記録しました');
      onSaved();
    } catch (error) {
      toast.error(getApiErrorMessage(error, '当日実績の記録に失敗しました'));
    } finally {
      setRecordingActual(false);
    }
  };

  const handleDelete = async () => {
    if (!editing) return;
    try {
      await shiftApi.delete(editing.id ?? '');
      toast.success('シフトを削除しました');
      onSaved();
      onClose();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'シフトの削除に失敗しました'));
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
        <DialogTitle className="border-b px-6 py-4">
          {editing ? 'シフトを編集' : 'シフトを追加'}
        </DialogTitle>
        <Form {...form}>
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
            <FormField
              control={control}
              name="cast_id"
              rules={{ required: true }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>キャスト</FormLabel>
                  <Select
                    value={field.value ? field.value : SELECT_NONE}
                    onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                  >
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {casts.length === 0 && (
                        <SelectItem value={SELECT_NONE}>キャストが未登録です</SelectItem>
                      )}
                      {casts.map(c => {
                        const id = c.id;
                        if (id === undefined) return null;
                        return (
                          <SelectItem key={id} value={id}>
                            {c.name}
                          </SelectItem>
                        );
                      })}
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />
            <div className="grid gap-2">
              <Label htmlFor="work_date">日付</Label>
              <Input id="work_date" type="date" {...register('work_date', { required: true })} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="start_time">開始</Label>
                <Input
                  id="start_time"
                  type="time"
                  {...register('start_time', { required: true })}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="end_time">終了</Label>
                <Input id="end_time" type="time" {...register('end_time', { required: true })} />
              </div>
            </div>
            <p className="text-xs text-muted-foreground">
              終了が開始以前のときは翌日にまたがる勤務として扱います。
            </p>
            <FormField
              control={control}
              name="status"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>ステータス</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {STATUS_OPTIONS.map(o => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />
            <label className="flex items-center gap-2 text-sm text-foreground">
              <input type="checkbox" {...register('public_visible')} />
              公開出勤表に表示する
            </label>
            {editing && (
              <div className="space-y-3 border-t pt-4">
                <p className="text-sm font-medium text-foreground">当日実績</p>
                <div className="grid grid-cols-2 gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="actual_start_time">実績開始</Label>
                    <Input
                      id="actual_start_time"
                      type="time"
                      value={actualStartTime}
                      disabled={actualUnavailableMessage !== null}
                      onChange={event => setActualStartTime(event.target.value)}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="actual_end_time">実績終了</Label>
                    <Input
                      id="actual_end_time"
                      type="time"
                      value={actualEndTime}
                      disabled={actualUnavailableMessage !== null}
                      onChange={event => setActualEndTime(event.target.value)}
                    />
                  </div>
                </div>
                {actualUnavailableMessage && (
                  <p className="text-xs text-muted-foreground">{actualUnavailableMessage}</p>
                )}
                <Button
                  type="button"
                  variant="outline"
                  disabled={
                    actualUnavailableMessage !== null ||
                    !actualStartTime ||
                    !actualEndTime ||
                    recordingActual
                  }
                  onClick={() => void recordActual()}
                >
                  {recordingActual ? '記録中...' : '実績を記録'}
                </Button>
              </div>
            )}
            <div className="flex items-center justify-between border-t pt-4">
              <div>
                {editing && (
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => setConfirmOpen(true)}
                    className="text-destructive-strong"
                  >
                    削除
                  </Button>
                )}
              </div>
              <div className="flex gap-3">
                <Button type="button" variant="outline" onClick={onClose}>
                  キャンセル
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? '保存中...' : '保存する'}
                </Button>
              </div>
            </div>
          </form>
        </Form>
        <ConfirmDialog
          open={confirmOpen}
          title="このシフトを削除しますか？"
          onConfirm={() => void handleDelete()}
          onClose={() => setConfirmOpen(false)}
        />
      </DialogContent>
    </Dialog>
  );
}
