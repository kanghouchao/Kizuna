'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import {
  CastShiftRequestItem,
  CastStoreItem,
  ShiftRequestCreateRequest,
  shiftApi,
} from '@/entities/shift';
import { toDateStr } from '../lib/week';

interface RequestFormValues {
  store_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  note: string;
}

const STATUS_LABELS: Record<CastShiftRequestItem['status'], string> = {
  PENDING: '受付済み',
  APPROVED: '確定済み',
  DECLINED: '却下',
};

const STATUS_PILL_CLASS: Record<CastShiftRequestItem['status'], string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-green-100 text-green-800',
  DECLINED: 'bg-red-100 text-red-800',
};

const inputClass =
  'w-full rounded-md border-gray-300 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500';

/** 明日の 'yyyy-MM-dd' を返す（提出フォームの初期日付・過去日拒否の下限に使う）。 */
function tomorrowStr(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return toDateStr(d);
}

function defaultValues(storeId: string): RequestFormValues {
  return { store_id: storeId, work_date: tomorrowStr(), start_time: '18:00', end_time: '23:00', note: '' };
}

/** 出勤希望の提出フォームと提出履歴。所属店を跨いで全量・新しい順に表示する。 */
export function CastRequestsPage() {
  const [stores, setStores] = useState<CastStoreItem[]>([]);
  const [history, setHistory] = useState<CastShiftRequestItem[] | null>(null);
  const [hasError, setHasError] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<RequestFormValues>({ defaultValues: defaultValues('') });

  const loadHistory = () => {
    setHistory(null);
    shiftApi
      .myShiftRequests()
      .then(res => setHistory(res))
      .catch(() => setHasError(true));
  };

  useEffect(() => {
    let cancelled = false;
    shiftApi
      .myStores()
      .then(res => {
        if (cancelled) return;
        setStores(res);
        if (res[0]) setValue('store_id', String(res[0].store_id));
      })
      .catch(() => {
        if (!cancelled) setHasError(true);
      });
    loadHistory();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = async (values: RequestFormValues) => {
    const payload: ShiftRequestCreateRequest = {
      store_id: Number(values.store_id),
      work_date: values.work_date,
      start_time: `${values.start_time}:00`,
      end_time: `${values.end_time}:00`,
      note: values.note || undefined,
    };
    try {
      await shiftApi.submitShiftRequest(payload);
      toast.success('出勤希望を提出しました');
      reset(defaultValues(values.store_id));
      loadHistory();
    } catch {
      toast.error('出勤希望の提出に失敗しました');
    }
  };

  return (
    <div className="space-y-6 p-4">
      <div>
        <h1 className="text-lg font-bold text-gray-900">希望提出</h1>
        <p className="mt-1 text-xs text-gray-500">出勤したい店舗・日時を提出できます。</p>
      </div>

      <form
        onSubmit={handleSubmit(submit)}
        className="space-y-4 rounded-[10px] border border-gray-200 bg-white p-4 shadow-sm"
      >
        <div>
          <label htmlFor="request-store" className="mb-1 block text-sm font-medium text-gray-700">
            店舗
          </label>
          <select
            id="request-store"
            {...register('store_id', { required: true })}
            className={inputClass}
          >
            {stores.length === 0 && <option value="">所属店舗がありません</option>}
            {stores.map(s => (
              <option key={s.store_id} value={s.store_id}>
                {s.store_name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="request-work-date" className="mb-1 block text-sm font-medium text-gray-700">
            日付
          </label>
          <input
            id="request-work-date"
            type="date"
            {...register('work_date', {
              required: true,
              validate: v => v >= tomorrowStr() || '本日以降の日付を指定してください',
            })}
            className={inputClass}
          />
          {errors.work_date && (
            <p className="mt-1 text-xs text-red-600">{errors.work_date.message}</p>
          )}
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label
              htmlFor="request-start-time"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              開始
            </label>
            <input
              id="request-start-time"
              type="time"
              {...register('start_time', { required: true })}
              className={inputClass}
            />
          </div>
          <div>
            <label
              htmlFor="request-end-time"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              終了
            </label>
            <input
              id="request-end-time"
              type="time"
              {...register('end_time', { required: true })}
              className={inputClass}
            />
          </div>
        </div>
        <div>
          <label htmlFor="request-note" className="mb-1 block text-sm font-medium text-gray-700">
            備考
          </label>
          <textarea
            id="request-note"
            {...register('note', {
              maxLength: { value: 500, message: '備考は500文字以内で入力してください' },
            })}
            rows={3}
            className={inputClass}
          />
          {errors.note && <p className="mt-1 text-xs text-red-600">{errors.note.message}</p>}
        </div>
        <button
          type="submit"
          disabled={isSubmitting || stores.length === 0}
          className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          {isSubmitting ? '提出中...' : '提出する'}
        </button>
      </form>

      <div>
        <h2 className="mb-2 text-sm font-semibold text-gray-900">提出履歴</h2>
        {hasError ? (
          <p className="text-sm text-red-600">履歴の取得に失敗しました</p>
        ) : history === null ? (
          <p className="text-sm text-gray-500">読み込み中...</p>
        ) : history.length === 0 ? (
          <p className="text-sm text-gray-500">提出履歴はありません</p>
        ) : (
          <ul className="space-y-2">
            {history.map(item => (
              <li
                key={item.id}
                className="rounded-[10px] border border-gray-200 bg-white p-3 shadow-sm"
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-900">{item.store_name}</span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_PILL_CLASS[item.status]}`}
                  >
                    {STATUS_LABELS[item.status]}
                  </span>
                </div>
                <p className="mt-1 text-xs text-gray-600">
                  {item.work_date} {item.start_time.slice(0, 5)}–{item.end_time.slice(0, 5)}
                </p>
                {item.note && <p className="mt-1 text-xs text-gray-500">{item.note}</p>}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
