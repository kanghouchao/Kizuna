'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { MemberOrder, ORDER_STATUS_LABELS, memberOrderApi } from '@/entities/order';
import { Badge, Button, Card, CardContent, CardHeader, CardTitle } from '@/shared/ui';

/** 予約の状態バッジ。確定前だけが取り下げ可能なので、申請中を強調する。 */
function StatusBadge({ status }: { status: MemberOrder['status'] }) {
  if (!status) return null;
  return (
    <Badge
      variant="outline"
      className={
        status === 'CREATED'
          ? 'border-transparent bg-primary/10 text-primary-strong'
          : 'border-transparent bg-muted text-foreground'
      }
    >
      {ORDER_STATUS_LABELS[status]}
    </Badge>
  );
}

/** 会員ポータルの予約一覧。全店舗を集約し、確定前のものは本人が取り下げられる。 */
export function MemberReservationsPage() {
  const [reservations, setReservations] = useState<MemberOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [processingId, setProcessingId] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    memberOrderApi
      .list()
      .then(page => {
        setReservations(page.rows);
        setFailed(false);
      })
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const cancel = async (id: string) => {
    setProcessingId(id);
    try {
      await memberOrderApi.cancel(id);
      toast.success('予約を取り下げました');
      load();
    } catch {
      toast.error('取り下げに失敗しました');
    } finally {
      setProcessingId(null);
    }
  };

  return (
    <div className="mx-auto w-full max-w-md p-4">
      <h1 className="mt-2 text-lg font-semibold text-foreground">予約</h1>
      <Card className="mt-4">
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            予約一覧
          </CardTitle>
        </CardHeader>
        <CardContent>
          {failed ? (
            <p className="text-sm text-destructive-strong">
              予約を取得できませんでした。再読み込みしてください。
            </p>
          ) : loading ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : reservations.length === 0 ? (
            <p className="text-sm text-muted-foreground">予約はまだありません。</p>
          ) : (
            <ul className="space-y-3">
              {reservations.map(reservation => (
                <li key={reservation.id} className="rounded-[10px] border bg-card p-4 shadow-sm">
                  <p className="flex items-center gap-2 text-sm font-medium text-foreground">
                    {reservation.store_name}
                    <StatusBadge status={reservation.status} />
                  </p>
                  <p className="text-sm text-muted-foreground">
                    {reservation.business_date}
                    {reservation.arrival_scheduled_start_time
                      ? ` ${reservation.arrival_scheduled_start_time.slice(0, 5)}`
                      : ''}
                    {reservation.pax ? ` / ${reservation.pax} 名` : ''}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    指名: {reservation.cast_name ?? 'なし'}
                  </p>
                  {reservation.status === 'CREATED' && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="mt-3"
                      onClick={() => cancel(reservation.id ?? '')}
                      disabled={processingId === reservation.id}
                    >
                      取り下げる
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
      <Button asChild variant="outline" className="mt-6 w-full">
        <Link href="/member/">ホームへ戻る</Link>
      </Button>
    </div>
  );
}
