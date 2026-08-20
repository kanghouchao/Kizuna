'use client';

import Link from 'next/link';
import { useState } from 'react';
import { notify } from '@/shared/notify';
import {
  MemberOrderApplication,
  ORDER_APPLICATION_STATUS_LABELS,
  memberOrderApplicationApi,
} from '@/entities/order';
import { getApiErrorMessage, useCursorList } from '@/shared/lib';
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, RegionError } from '@/shared/ui';

/**
 * 予約申請の状態バッジ。行の状態は PENDING のままでも、希望日を過ぎた申請はサーバの導出（expired）に
 * 従って失効と表示する — 画面が日時を比べると、営業日の区切り（日付変更時刻）とずれる。
 */
function StatusBadge({ application }: { application: MemberOrderApplication }) {
  if (!application.status) return null;
  if (application.status === 'PENDING' && application.expired) {
    return (
      <Badge variant="outline" className="border-transparent bg-muted text-muted-foreground">
        失効
      </Badge>
    );
  }
  const styles: Record<MemberOrderApplication['status'] & string, string> = {
    PENDING: 'border-transparent bg-warning/10 text-warning-strong',
    CONFIRMED: 'border-transparent bg-success/10 text-success-strong',
    DECLINED: 'border-transparent bg-destructive/10 text-destructive-strong',
    WITHDRAWN: 'border-transparent bg-muted text-foreground',
  };
  return (
    <Badge variant="outline" className={styles[application.status]}>
      {ORDER_APPLICATION_STATUS_LABELS[application.status]}
    </Badge>
  );
}

/** 1 回に読み込む件数。会員の画面は縦に伸ばす前提なので、追加読み込みで過去へ遡る。 */
const PAGE_SIZE = 20;

/** 会員ポータルの予約申請一覧。全店舗を集約し、未処理のものは本人が取り下げられる。 */
export function MemberReservationsPage() {
  const [processingId, setProcessingId] = useState<string | null>(null);
  // 1 回の取得は常に PAGE_SIZE 件で、続きは位置（カーソル）を渡して 1 回ずつ継ぎ足す。要求サイズ自体を
  // 膨らませると、サーバ側の取得上限に当たった時点でそれ以降の申請へ到達できなくなる。
  const {
    rows: reservations,
    setRows: setReservations,
    isLoading: loading,
    failed,
    hasMore,
    reload,
    loadMore,
  } = useCursorList<MemberOrderApplication>(cursor =>
    memberOrderApplicationApi.list({ cursor, size: PAGE_SIZE })
  );

  const withdraw = async (reservation: MemberOrderApplication) => {
    setProcessingId(reservation.id ?? null);
    try {
      // 取り下げても申請は一覧に残る（状態が変わるだけ）ので、その行だけ差し替える。
      const updated = await memberOrderApplicationApi.withdraw(reservation.id);
      notify.success('予約を取り下げました');
      setReservations(prev => prev.map(row => (row.id === reservation.id ? updated : row)));
    } catch (error) {
      notify.error(getApiErrorMessage(error, '取り下げに失敗しました'));
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
            <RegionError message="予約を取得できませんでした。" onRetry={reload} />
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
                    <StatusBadge application={reservation} />
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
                  {reservation.status === 'PENDING' && (
                    // 取り下げの結果が返るまで同じ行を押せると、済んだ取り下げをもう一度投げてしまう。
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="mt-3"
                      onClick={() => withdraw(reservation)}
                      disabled={loading || processingId === reservation.id}
                    >
                      取り下げる
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {hasMore && (
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
