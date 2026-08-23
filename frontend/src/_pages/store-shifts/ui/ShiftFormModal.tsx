'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { CastResponse } from '@/entities/cast';
import { ShiftResponse, shiftApi } from '@/entities/shift';
import { getApiErrorMessage } from '@/shared/lib';
import { SELECT_NONE, castValue } from '../lib/cast-select';
import { hhmm } from '../lib/datetime';
import { ShiftDialogShell } from './ShiftDialogShell';
import {
  Button,
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
  Switch,
} from '@/shared/ui';

interface ShiftFormValues {
  cast_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  status: string;
  published: boolean;
}

interface ShiftFormModalProps {
  open: boolean;
  onClose: () => void;
  casts: CastResponse[];
  /** 編集対象。null なら新規作成。 */
  editing: ShiftResponse | null;
  /**
   * 編集対象に未取消の当日実績が付いているか。実績は記録時に営業日とキャストを物化しているので、
   * 事後の付け替えは後端が拒む（ADR 0014）。この画面はその可否を先に映す。
   */
  hasAttendance: boolean;
  /** 新規作成時の初期日付 'yyyy-MM-dd'。 */
  defaultDate: string;
  /** 保存の成功後に呼ばれる（一覧の再取得用）。 */
  onSaved: () => void;
}

const STATUS_OPTIONS = [
  { value: 'TENTATIVE', label: '未確定' },
  { value: 'CONFIRMED', label: '確定' },
];

/** シフトの追加・編集モーダル。 */
export function ShiftFormModal({
  open,
  onClose,
  casts,
  editing,
  hasAttendance,
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
      published: true,
    },
  });
  const {
    handleSubmit,
    reset,
    control,
    formState: { isSubmitting },
  } = form;
  // 実績が付くのは既存のシフトだけ。新規作成の面は何も塞がない
  const locked = editing !== null && hasAttendance;

  // 候補は Select と項目描画の両方が読む。引き金に出る文言は items から引かれるので、
  // 選べる値の一覧はここ一箇所に持つ。
  const castOptions =
    casts.length === 0
      ? [{ value: SELECT_NONE, label: 'キャストが未登録です' }]
      : casts
          .filter(c => c.id !== undefined)
          .map(c => ({ value: c.id as string, label: c.name ?? '' }));

  useEffect(() => {
    if (!open) return;
    if (editing) {
      reset({
        cast_id: editing.cast_id ?? '',
        work_date: editing.work_date ?? '',
        start_time: hhmm(editing.start_time),
        end_time: hhmm(editing.end_time),
        status: editing.status ?? '',
        published: editing.published ?? true,
      });
    } else {
      reset({
        cast_id: casts[0]?.id ?? '',
        work_date: defaultDate,
        start_time: '18:00',
        end_time: '23:00',
        status: 'TENTATIVE',
        published: true,
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
    };
    try {
      if (editing) {
        // 更新の送信物は公開可否の欄を持たない — 切替は専用の口が受ける
        await shiftApi.update(editing.id, payload);
        notify.success('シフトを更新しました');
      } else {
        await shiftApi.create({ ...payload, published: values.published });
        notify.success('シフトを追加しました');
      }
      onSaved();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'シフトの保存に失敗しました'));
    }
  };

  return (
    <ShiftDialogShell
      open={open}
      onClose={onClose}
      title={editing ? 'シフトを編集' : 'シフトを追加'}
    >
      <Form {...form}>
        {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は各 rules が担う */}
        <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
          {/* 塞いだ欄の手前で理由を述べる。無効化したコントロールは焦点を取れず、欄に紐づけた説明は
                読み上げに届かない — 押せない口の理由は本文の側に置く */}
          {locked && (
            <div className="rounded-md bg-muted px-3 py-2 text-xs text-foreground">
              勤務日とキャストの変更 —
              当日実績が記録されているため行えません。当日実績タブで実績を取り消してから行います。
            </div>
          )}
          <FormField
            control={control}
            name="cast_id"
            rules={{ required: 'キャストを選択してください' }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>キャスト</FormLabel>
                <Select
                  items={castOptions}
                  value={castValue(field.value, castOptions)}
                  onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                  disabled={locked}
                  required
                >
                  <FormControl>
                    {/* handleSubmit の焦点移動は登録された ref を叩く。ref が trigger へ
                          届かないと、文言だけ出て焦点が動かない。 */}
                    <SelectTrigger className="w-full" ref={field.ref}>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {castOptions.map(o => (
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
          <FormField
            control={control}
            name="work_date"
            rules={{ required: '日付を入力してください' }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>日付</FormLabel>
                <FormControl>
                  <Input type="date" required disabled={locked} {...field} />
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
          <p className="text-xs text-muted-foreground">
            終了が開始以前のときは翌日にまたがる勤務として扱います。
          </p>
          <FormField
            control={control}
            name="status"
            render={({ field }) => (
              <FormItem>
                <FormLabel>ステータス</FormLabel>
                <Select items={STATUS_OPTIONS} value={field.value} onValueChange={field.onChange}>
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
          {/* 出生時だけこの口が決める（理由は lib/publication.ts）。編集では出さない — 既に目玉と
                公開パネルという二つの入口があり、三つ目は保存のたびに切替をもう一度打つ経路を
                増やすだけになる */}
          {!editing && (
            <FormField
              control={control}
              name="published"
              render={({ field }) => (
                <FormItem className="flex items-center justify-between gap-3">
                  <div>
                    <FormLabel>公式サイトに公開する</FormLabel>
                    <FormDescription className="mt-1 text-xs">
                      確定シフトだけが出勤表に出ます。内密の出勤はここで外してから追加します。
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          )}
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
    </ShiftDialogShell>
  );
}
