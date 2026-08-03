'use client';

import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { Order, orderApi } from '@/entities/order';
import { Badge, Button } from '@/shared/ui';

interface ReservationRequestInboxProps {
  /** 確定・謝絶の成功後に呼ばれる（同じ受注の状態が変わるため、一覧の再取得に使う）。 */
  onProcessed: () => void;
}

/**
 * 予約受付 inbox。会員ポータルからの未確定申請だけを表示する。
 *
 * 絞り込みはサーバ側の専用読み口が行う。受注一覧を取って手元で選り分けると、
 * 受付経路 WEB を手で付けた店舗起点の受注まで拾ってしまい、かつ確定済みが積み上がった
 * 店舗では未処理の申請が取得窓から落ちて見えなくなる。
 */
export function ReservationRequestInbox({ onProcessed }: ReservationRequestInboxProps) {
  const [requests, setRequests] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    orderApi
      .listReservationRequests()
      .then(setRequests)
      .catch(() => toast.error('予約申請の取得に失敗しました'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const process = async (id: string, action: 'confirm' | 'decline') => {
    setProcessingId(id);
    try {
      if (action === 'confirm') {
        await orderApi.confirm(id);
        toast.success('予約を確定しました');
      } else {
        await orderApi.decline(id);
        toast.success('予約を謝絶しました');
      }
      load();
      onProcessed();
    } catch {
      toast.error(action === 'confirm' ? '確定に失敗しました' : '謝絶に失敗しました');
    } finally {
      setProcessingId(null);
    }
  };

  if (loading) {
    return <p className="text-sm text-muted-foreground">読み込み中...</p>;
  }
  if (requests.length === 0) {
    return <p className="text-sm text-muted-foreground">未確定の予約申請はありません</p>;
  }

  return (
    <ul className="space-y-3">
      {requests.map(request => (
        <li
          key={request.id}
          className="flex items-center justify-between rounded-[10px] border bg-card p-4 shadow-sm"
        >
          <div>
            <p className="flex items-center gap-2 text-sm font-medium text-foreground">
              {request.business_date}
              <Badge
                variant="outline"
                className="border-transparent bg-primary/10 text-primary-strong"
              >
                WEB申請
              </Badge>
            </p>
            <p className="text-sm text-muted-foreground">
              {request.arrival_scheduled_start_time?.slice(0, 5) ?? '時刻未定'}
              {request.pax != null ? ` / ${request.pax} 名` : ''} / 指名:{' '}
              {request.cast_name ?? 'なし'}
            </p>
            <p className="text-xs text-muted-foreground">
              会員コード: {request.requester_member_code ?? '-'}
            </p>
            {request.remarks && (
              <p className="mt-1 text-xs text-muted-foreground">{request.remarks}</p>
            )}
          </div>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => process(request.id ?? '', 'decline')}
              disabled={processingId === request.id}
            >
              謝絶
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={() => process(request.id ?? '', 'confirm')}
              disabled={processingId === request.id}
            >
              確定
            </Button>
          </div>
        </li>
      ))}
    </ul>
  );
}
