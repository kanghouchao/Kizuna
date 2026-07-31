'use client';

import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { CastResponse } from '@/entities/cast';
import { StoreShiftRequestItem, shiftApi } from '@/entities/shift';
import { Badge, Button } from '@/shared/ui';

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

  // castId が未指定のとき、id を持たないキャストと undefined 同士で一致してしまうのを防ぐ
  const castName = (castId: string | undefined) =>
    (castId === undefined ? undefined : casts.find(c => c.id === castId)?.name) ?? castId;

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
    return <p className="text-sm text-muted-foreground">読み込み中...</p>;
  }
  if (requests.length === 0) {
    return <p className="text-sm text-muted-foreground">受付中の出勤希望はありません</p>;
  }

  return (
    <ul className="space-y-3">
      {requests.map(request => (
        <li
          key={request.id}
          className="flex items-center justify-between rounded-[10px] border bg-card p-4 shadow-sm"
        >
          <div>
            <div className="flex items-center gap-2">
              <p className="text-sm font-medium text-foreground">{castName(request.cast_id)}</p>
              <Badge variant="outline">{request.kind === 'CHANGE' ? '変更申請' : '新規希望'}</Badge>
            </div>
            {request.kind === 'CHANGE' && !request.target_shift_id ? (
              <p className="text-sm text-destructive-strong">対象シフトは削除されました</p>
            ) : request.kind === 'CHANGE' ? (
              <>
                <p className="text-sm text-muted-foreground">
                  {request.current_work_date} {request.current_start_time?.slice(0, 5)}–
                  {request.current_end_time?.slice(0, 5)}
                </p>
                <p className="text-sm font-medium text-foreground">
                  {request.work_date} {request.start_time?.slice(0, 5)}–
                  {request.end_time?.slice(0, 5)}
                </p>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">
                {request.work_date} {request.start_time?.slice(0, 5)}–
                {request.end_time?.slice(0, 5)}
              </p>
            )}
            {request.note && <p className="mt-1 text-xs text-muted-foreground">{request.note}</p>}
          </div>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => decline(request.id ?? '')}
              disabled={processingId === request.id}
            >
              {request.kind === 'CHANGE' ? '謝絶' : '辞退'}
            </Button>
            {(request.kind !== 'CHANGE' || request.target_shift_id) && (
              <Button
                type="button"
                size="sm"
                onClick={() => approve(request.id ?? '')}
                disabled={processingId === request.id}
              >
                {request.kind === 'CHANGE' ? '承認してシフト更新' : '承認'}
              </Button>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
