'use client';

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { CastResponse } from '@/entities/cast';
import { ShiftResponse, shiftApi } from '@/entities/shift';

interface ShiftFormValues {
  cast_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  status: string;
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

const inputClass =
  'w-full rounded-md border-gray-300 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500';

/** シフトの追加・編集モーダル。 */
export function ShiftFormModal({
  open,
  onClose,
  casts,
  editing,
  defaultDate,
  onSaved,
}: ShiftFormModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<ShiftFormValues>();

  useEffect(() => {
    if (!open) return;
    if (editing) {
      reset({
        cast_id: editing.cast_id,
        work_date: editing.work_date,
        start_time: editing.start_time.slice(0, 5),
        end_time: editing.end_time.slice(0, 5),
        status: editing.status,
      });
    } else {
      reset({
        cast_id: casts[0]?.id ?? '',
        work_date: defaultDate,
        start_time: '18:00',
        end_time: '23:00',
        status: 'TENTATIVE',
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
        await shiftApi.update(editing.id, payload);
        toast.success('シフトを更新しました');
      } else {
        await shiftApi.create(payload);
        toast.success('シフトを追加しました');
      }
      onSaved();
      onClose();
    } catch {
      toast.error('シフトの保存に失敗しました');
    }
  };

  const handleDelete = async () => {
    if (!editing) return;
    if (!confirm('このシフトを削除しますか？')) return;
    try {
      await shiftApi.delete(editing.id);
      toast.success('シフトを削除しました');
      onSaved();
      onClose();
    } catch {
      toast.error('シフトの削除に失敗しました');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} className="relative z-50">
      <div className="fixed inset-0 bg-gray-900/40" aria-hidden="true" />
      <div className="fixed inset-0 flex items-center justify-center p-4">
        <DialogPanel className="w-full max-w-md rounded-[10px] border border-gray-200 bg-white shadow-lg">
          <DialogTitle className="border-b border-gray-200 px-6 py-4 text-lg font-semibold text-gray-900">
            {editing ? 'シフトを編集' : 'シフトを追加'}
          </DialogTitle>
          <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">キャスト</label>
              <select {...register('cast_id', { required: true })} className={inputClass}>
                {casts.length === 0 && <option value="">キャストが未登録です</option>}
                {casts.map(c => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">日付</label>
              <input
                type="date"
                {...register('work_date', { required: true })}
                className={inputClass}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">開始</label>
                <input
                  type="time"
                  {...register('start_time', { required: true })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">終了</label>
                <input
                  type="time"
                  {...register('end_time', { required: true })}
                  className={inputClass}
                />
              </div>
            </div>
            <p className="text-xs text-gray-500">
              終了が開始以前のときは翌日にまたがる勤務として扱います。
            </p>
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">ステータス</label>
              <select {...register('status')} className={inputClass}>
                {STATUS_OPTIONS.map(o => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex items-center justify-between border-t border-gray-200 pt-4">
              <div>
                {editing && (
                  <button
                    type="button"
                    onClick={handleDelete}
                    className="rounded text-sm font-medium text-red-600 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                  >
                    削除
                  </button>
                )}
              </div>
              <div className="flex gap-3">
                <button
                  type="button"
                  onClick={onClose}
                  className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                >
                  キャンセル
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="rounded-md bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                >
                  {isSubmitting ? '保存中...' : '保存する'}
                </button>
              </div>
            </div>
          </form>
        </DialogPanel>
      </div>
    </Dialog>
  );
}
