'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
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
  /** 新規作成時の初期日付 'yyyy-MM-dd'。 */
  defaultDate: string;
  /** 保存・削除の成功後に呼ばれる（一覧の再取得用）。 */
  onSaved: () => void;
}

const STATUS_OPTIONS = [
  { value: 'TENTATIVE', label: '未確定' },
  { value: 'CONFIRMED', label: '確定' },
];

// 「キャスト未登録」の案内項目に与える番兵値。onValueChange で '' に戻すことで、
// フォームが持つ値は従来どおり空文字になる。
const SELECT_NONE = '__none__';

/**
 * 引き金に出す値。候補に無い値（在籍を外れたキャストなど）は未選択として null に倒す。
 * 引き金の文言は候補一覧から引かれるので、素通しすると生の ID がそのまま出る。
 */
function castValue(value: string, options: { value: string }[]): string | null {
  const candidate = value || SELECT_NONE;
  return options.some(o => o.value === candidate) ? candidate : null;
}

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
      published: true,
    },
  });
  const {
    handleSubmit,
    reset,
    control,
    formState: { isSubmitting },
  } = form;
  const [confirmOpen, setConfirmOpen] = useState(false);

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
        start_time: (editing.start_time ?? '').slice(0, 5),
        end_time: (editing.end_time ?? '').slice(0, 5),
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
        await shiftApi.update(editing.id ?? '', payload);
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

  const handleDelete = async () => {
    if (!editing) return;
    try {
      await shiftApi.delete(editing.id ?? '');
      notify.success('シフトを削除しました');
      onSaved();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'シフトの削除に失敗しました'));
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
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は各 rules が担う */}
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5" noValidate>
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
                      <FormLabel htmlFor="shift-published">公式サイトに公開する</FormLabel>
                      <p className="mt-1 text-xs text-muted-foreground">
                        確定シフトだけが出勤表に出ます。内密の出勤はここで外してから追加します。
                      </p>
                    </div>
                    <Switch
                      id="shift-published"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </FormItem>
                )}
              />
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
