'use client';

import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { MEMBER_ORDER_STATUS_LABELS, MemberOrder, memberOrderApi } from '@/entities/order';
import { useCursorList } from '@/shared/lib';
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
      {MEMBER_ORDER_STATUS_LABELS[status]}
    </Badge>
  );
}

/** 1 回に読み込む件数。会員の画面は縦に伸ばす前提なので、追加読み込みで過去へ遡る。 */
const PAGE_SIZE = 20;

/** 会員ポータルの予約一覧。全店舗を集約し、確定前のものは本人が取り下げられる。 */
export function MemberReservationsPage() {
  const [processingId, setProcessingId] = useState<string | null>(null);
  // 1 回の取得は常に PAGE_SIZE 件で、続きは位置（カーソル）を渡して 1 回ずつ継ぎ足す。要求サイズ自体を
  // 膨らませると、サーバ側の取得上限に当たった時点でそれ以降の予約へ到達できなくなる。
  const {
    rows: reservations,
    setRows: setReservations,
    isLoading: loading,
    failed,
    loadMoreFailed,
    hasMore,
    loadMore,
  } = useCursorList<MemberOrder>(cursor => memberOrderApi.list({ cursor, size: PAGE_SIZE }));

  const cancel = async (id: string) => {
    setProcessingId(id);
    try {
      // 取り下げても予約は一覧に残る（状態が変わるだけ）ので、その行だけ差し替える。
      const updated = await memberOrderApi.cancel(id);
      toast.success('予約を取り下げました');
      setReservations(prev =>
        prev.map(reservation => (reservation.id === id ? updated : reservation))
      );
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
          ) : loading && reservations.length === 0 ? (
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
                    // 取り下げの結果が返るまで同じ行を押せると、済んだ取り下げをもう一度投げてしまう。
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="mt-3"
                      onClick={() => cancel(reservation.id ?? '')}
                      disabled={loading || processingId === reservation.id}
                    >
                      取り下げる
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {/* 追加読み込みの失敗は、失敗した拡張だけを再試行できる形で出す。全体を失敗表示に
              置き換えると、すでに読み込めていた予約（取り下げられるもの）まで消えてしまう。 */}
          {loadMoreFailed && (
            <div className="mt-4 space-y-2">
              <p className="text-sm text-destructive-strong">
                予約を追加で取得できませんでした。表示は前回の取得内容です。
              </p>
              <Button
                type="button"
                variant="outline"
                className="w-full"
                onClick={loadMore}
                disabled={loading}
              >
                再試行
              </Button>
            </div>
          )}
          {hasMore && !loadMoreFailed && (
            <Button
              type="button"
              variant="outline"
              className="mt-4 w-full"
              onClick={loadMore}
              disabled={loading}
            >
              もっと見る
            </Button>
          )}
        </CardContent>
      </Card>
      <Button render={<Link href="/member/" />} variant="outline" className="mt-6 w-full">
        ホームへ戻る
      </Button>
    </div>
  );
}
