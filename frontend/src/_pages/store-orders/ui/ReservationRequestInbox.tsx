'use client';

import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { Order, orderApi } from '@/entities/order';
import { Badge, Button } from '@/shared/ui';

interface ReservationRequestInboxProps {
  /** 確定・謝絶の成功後に呼ばれる（同じ受注の状態が変わるため、一覧の再取得に使う）。 */
  onProcessed: () => void;
}

/** inbox が拾う件数の上限。未処理の申請は溜め込まない運用のため、一覧の 1 ページ分で足りる。 */
const FETCH_SIZE = 50;

/**
 * 予約受付 inbox。会員ポータルからの未確定申請（WEB 受付の CREATED）だけを表示する。
 *
 * <p>店舗が起こした未確定の受注は申請ではないため出さない — 確定・謝絶はあくまで申請への応答である。
 */
export function ReservationRequestInbox({ onProcessed }: ReservationRequestInboxProps) {
  const [requests, setRequests] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    orderApi
      .list({ page: 0, size: FETCH_SIZE, sort: 'createdAt,id,desc' })
      .then(page =>
        setRequests(
          page.rows.filter(order => order.status === 'CREATED' && order.reception_route === 'WEB')
        )
      )
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
