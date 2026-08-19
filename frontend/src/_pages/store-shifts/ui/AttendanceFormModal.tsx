'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { CastResponse } from '@/entities/cast';
import { AttendanceResponse, ShiftResponse, attendanceApi } from '@/entities/shift';
import { getApiErrorMessage } from '@/shared/lib';
import { SELECT_NONE, castValue } from '../lib/castSelect';
import { dateTimeInputOn, hhmm, toDateTimeInput } from '../lib/datetime';
import { castName } from '../lib/labels';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';

/** 実績フォームの宛先。訂正は既存行を、記録はシフト紐づきか飛び込みかを指す。 */
export interface AttendanceFormTarget {
  /** 訂正の対象。null なら新規記録。 */
  attendance: AttendanceResponse | null;
  /** 紐づく確定シフト。null は飛び込み（予定なし）。訂正では表示のためだけに使う。 */
  shift: ShiftResponse | null;
}

interface AttendanceFormModalProps {
  open: boolean;
  onClose: () => void;
  /** open が false の間は null。 */
  target: AttendanceFormTarget | null;
  /** 飛び込みの記録で選べるキャスト。同営業日に実績を持つキャストは呼び出し側が除いて渡す。 */
  castOptions: CastResponse[];
  /** 名前解決用の名簿（除外前の全量）。 */
  casts: CastResponse[];
  /** 表示中の営業日 'yyyy-MM-dd'。飛び込みの実開始の初期値がこの日に載る。 */
  defaultDate: string;
  /** 保存の成功後に呼ばれる（実績・欠勤の再取得用）。 */
  onSaved: () => void;
}

interface AttendanceFormValues {
  cast_id: string;
  business_date: string;
  actual_start_at: string;
  actual_end_at: string;
  waiting_place: string;
}

/** 当日実績の記録・訂正モーダル。キャストとシフトの付け替えは載せない — 逃げ道は取消 → 再記録（ADR 0014）。 */
export function AttendanceFormModal({
  open,
  onClose,
  target,
  castOptions,
  casts,
  defaultDate,
  onSaved,
}: AttendanceFormModalProps) {
  const form = useForm<AttendanceFormValues>({
    defaultValues: {
      cast_id: '',
      business_date: '',
      actual_start_at: '',
      actual_end_at: '',
      waiting_place: '',
    },
  });
  const {
    handleSubmit,
    reset,
    control,
    formState: { isSubmitting },
  } = form;

  const attendance = target?.attendance ?? null;
  const shift = target?.shift ?? null;
  const correcting = attendance !== null;
  // 飛び込みかどうかは宛先が決める。訂正では既存行が、記録ではシフトの有無が根拠になる。
  const walkIn = correcting ? attendance.shift_id === undefined : shift === null;

  const selectOptions =
    castOptions.length === 0
      ? [{ value: SELECT_NONE, label: '記録できるキャストがいません' }]
      : castOptions
          .filter(c => c.id !== undefined)
          .map(c => ({ value: c.id as string, label: c.name ?? '' }));

  useEffect(() => {
    if (!open || target === null) return;
    if (target.attendance) {
      reset({
        cast_id: target.attendance.cast_id ?? '',
        business_date: target.attendance.business_date ?? '',
        actual_start_at: toDateTimeInput(target.attendance.actual_start_at),
        actual_end_at: toDateTimeInput(target.attendance.actual_end_at),
        waiting_place: target.attendance.waiting_place ?? '',
      });
      return;
    }
    // 勤務日は暦日ではなく営業日なので、日付変更時刻より前に始まる枠の実際の暦日は翌日になる。
    // 予定と表示日は初期値としてだけ置き、暦日の食い違いは欄の上で直してもらう。飛び込みを
    // 現在時刻だけで起こすと、別の日を見ている操作が黙って今日の営業日へ落ちる。
    reset({
      cast_id: target.shift?.cast_id ?? '',
      business_date: '',
      actual_start_at: target.shift
        ? `${target.shift.work_date}T${hhmm(target.shift.start_time)}`
        : dateTimeInputOn(defaultDate, new Date()),
      actual_end_at: '',
      waiting_place: '',
    });
  }, [open, target, defaultDate, reset]);

  const submit = async (values: AttendanceFormValues) => {
    if (target === null) return;
    // 空欄は「値なし」として送る。空文字のまま送ると待機場所に空の記録が残る
    const common = {
      actual_start_at: values.actual_start_at,
      actual_end_at: values.actual_end_at || null,
      waiting_place: values.waiting_place.trim() || null,
    };
    try {
      if (target.attendance) {
        await attendanceApi.correct(target.attendance.id ?? '', {
          business_date: values.business_date,
          ...common,
        });
        notify.success('当日実績を訂正しました');
      } else {
        await attendanceApi.record({
          cast_id: values.cast_id,
          shift_id: target.shift?.id ?? null,
          ...common,
        });
        notify.success('当日実績を記録しました');
      }
      onSaved();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '当日実績の保存に失敗しました'));
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
          {correcting ? '当日実績を訂正' : '当日実績を記録'}
        </DialogTitle>
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は各 rules が担う */}
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
            {/* 記録済みの実績も、シフトから起こす記録も、キャストは動かせない。選ばせるのは
                飛び込みを新しく起こすときだけ */}
            {correcting || !walkIn ? (
              <div>
                <p className="text-sm font-medium text-foreground">キャスト</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {castName(casts, attendance?.cast_id ?? shift?.cast_id)}
                </p>
              </div>
            ) : (
              <FormField
                control={control}
                name="cast_id"
                rules={{ required: 'キャストを選択してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>キャスト</FormLabel>
                    {/* 誰が出勤したかに既定値は置かない。シフトの追加と違い、実績は特定の人物に
                        ついての法定保存の記録で、既定のまま保存された誤りは訂正履歴に残る */}
                    <Select
                      items={selectOptions}
                      value={castValue(field.value, selectOptions)}
                      onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                      required
                    >
                      <FormControl>
                        {/* handleSubmit の焦点移動は登録された ref を叩く */}
                        <SelectTrigger className="w-full" ref={field.ref}>
                          <SelectValue placeholder="キャストを選択" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {selectOptions.map(o => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}

            <div>
              <p className="text-sm font-medium text-foreground">予定</p>
              <p className="mt-1 text-sm text-muted-foreground">
                {shift
                  ? `${shift.work_date} ${hhmm(shift.start_time)}–${hhmm(shift.end_time)}`
                  : '飛び込み（予定なし）'}
              </p>
            </div>

            {/* 飛び込みの帰属営業日だけは訂正できる。日付変更時刻の変更は不遡及なので、変更前に
                起きた飛び込みを後から補記したときの誤帰属はここでしか直せない。シフト紐づきは
                勤務日の継承であって選択ではない */}
            {correcting && walkIn && (
              <FormField
                control={control}
                name="business_date"
                rules={{ required: '帰属営業日を入力してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>帰属営業日</FormLabel>
                    <FormControl>
                      <Input type="date" required {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}

            <FormField
              control={control}
              name="actual_start_at"
              rules={{ required: '実際の開始を入力してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>実際の開始</FormLabel>
                  <FormControl>
                    <Input type="datetime-local" required {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="actual_end_at"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>実際の終了</FormLabel>
                  <FormControl>
                    <Input type="datetime-local" {...field} />
                  </FormControl>
                  <FormDescription className="text-xs">
                    まだ退勤していないときは空のままにします。
                  </FormDescription>
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="waiting_place"
              rules={{
                maxLength: { value: 200, message: '待機場所は 200 文字以内で入力してください' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>待機場所</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

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
