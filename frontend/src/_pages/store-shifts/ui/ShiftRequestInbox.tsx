'use client';

import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { CastResponse } from '@/entities/cast';
import { StoreShiftRequestItem, shiftApi } from '@/entities/shift';

interface ShiftRequestInboxProps {
  casts: CastResponse[];
  /** 承認成功後に呼ばれる（確定シフトが新規作成されるため、シフト一覧の再取得に使う）。 */
  onApproved: () => void;
}

/** 出勤希望 inbox。受付済み(PENDING)のみを表示する — 処理済みの閲覧は cast 側履歴の責務。 */
export function ShiftRequestInbox({ casts, onApproved }: ShiftRequestInboxProps) {
  const [requests, setRequests] = useState<StoreShiftRequestItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);

  const castName = (castId: string) => casts.find(c => c.id === castId)?.name ?? castId;

  const load = () => {
    setLoading(true);
    shiftApi
      .listShiftRequests({ status: 'PENDING' })
      .then(setRequests)
      .catch(() => toast.error('出勤希望の取得に失敗しました'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const approve = async (id: string) => {
    setProcessingId(id);
    try {
      await shiftApi.approveShiftRequest(id);
      toast.success('出勤希望を承認しました');
      load();
      onApproved();
    } catch {
      toast.error('承認に失敗しました');
    } finally {
      setProcessingId(null);
    }
  };

  const decline = async (id: string) => {
    setProcessingId(id);
    try {
      await shiftApi.declineShiftRequest(id);
      toast.success('出勤希望を却下しました');
      load();
    } catch {
      toast.error('却下に失敗しました');
    } finally {
      setProcessingId(null);
    }
  };

  if (loading) {
    return <p className="text-sm text-gray-500">読み込み中...</p>;
  }
  if (requests.length === 0) {
    return <p className="text-sm text-gray-500">受付中の出勤希望はありません</p>;
  }

  return (
    <ul className="space-y-3">
      {requests.map(request => (
        <li
          key={request.id}
          className="flex items-center justify-between rounded-[10px] border border-gray-200 bg-white p-4 shadow-sm"
        >
          <div>
            <p className="text-sm font-medium text-gray-900">{castName(request.cast_id)}</p>
            <p className="text-sm text-gray-600">
              {request.work_date} {request.start_time.slice(0, 5)}–{request.end_time.slice(0, 5)}
            </p>
            {request.note && <p className="mt-1 text-xs text-gray-500">{request.note}</p>}
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => decline(request.id)}
              disabled={processingId === request.id}
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              辞退
            </button>
            <button
              type="button"
              onClick={() => approve(request.id)}
              disabled={processingId === request.id}
              className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              承認
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
