'use client';

import Link from 'next/link';
import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'react-hot-toast';
import { MEMBER_ORDER_STATUS_LABELS, MemberOrder, memberOrderApi } from '@/entities/order';
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
  const [reservations, setReservations] = useState<MemberOrder[]>([]);
  const [loading, setLoading] = useState(true);
  // 表示できるものが何も無い状態での取得失敗。空表示（＝予約なし）と区別する。
  const [failed, setFailed] = useState(false);
  // 表示中の予約を保ったまま失敗した取得の対象ページ数（再試行にそのまま使う）。
  const [failedPages, setFailedPages] = useState<number | null>(null);
  const [processingId, setProcessingId] = useState<string | null>(null);
  // 読み込み済みのページ数。追加読み込み後の再取得でも同じ範囲を取り直せるように保持する。
  const [loadedPages, setLoadedPages] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  // 失敗時の分岐に使う「今表示している件数」。load を再生成しない（mount 用 effect を引き直さない）ため
  // state を読まずに済ませる。
  const shownCount = useRef(0);

  const load = useCallback((pages: number) => {
    setLoading(true);
    setFailedPages(null);
    setFailed(false);
    // 取り下げ後も表示中の範囲を保つため、読み込み済みページ分をまとめて取り直す。
    const size = pages * PAGE_SIZE;
    memberOrderApi
      .list({ page: 0, size })
      .then(page => {
        shownCount.current = page.rows.length;
        setReservations(page.rows);
        // 要求した件数より少なく返ってきたのに残りがある＝サーバ側の上限に当たっている。
        // ここで「もっと見る」を出し続けると、押しても増えないボタンになる。
        setHasMore(page.rows.length < page.total && page.rows.length >= size);
        setLoadedPages(pages);
      })
      .catch(() => {
        // 表示中の予約があるなら消さない — 追加読み込みの失敗で既読み込み分まで失うと、
        // 取り下げられる予約すら見えなくなる。読み込み済みページ数も進めない。
        if (shownCount.current === 0) {
          setFailed(true);
        } else {
          setFailedPages(pages);
        }
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load(1);
  }, [load]);

  const cancel = async (id: string) => {
    setProcessingId(id);
    try {
      await memberOrderApi.cancel(id);
      toast.success('予約を取り下げました');
      load(loadedPages);
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
          {/* 追加読み込みの失敗は、失敗した拡張だけを再試行できる形で出す。全体を失敗表示に
              置き換えると、すでに読み込めていた予約（取り下げられるもの）まで消えてしまう。 */}
          {failedPages !== null && (
            <div className="mt-4 space-y-2">
              <p className="text-sm text-destructive-strong">
                予約を追加で取得できませんでした。表示は前回の取得内容です。
              </p>
              <Button
                type="button"
                variant="outline"
                className="w-full"
                onClick={() => load(failedPages)}
                disabled={loading}
              >
                再試行
              </Button>
            </div>
          )}
          {hasMore && failedPages === null && (
            <Button
              type="button"
              variant="outline"
              className="mt-4 w-full"
              onClick={() => load(loadedPages + 1)}
              disabled={loading}
            >
              もっと見る
            </Button>
          )}
        </CardContent>
      </Card>
      <Button asChild variant="outline" className="mt-6 w-full">
        <Link href="/member/">ホームへ戻る</Link>
      </Button>
    </div>
  );
}
