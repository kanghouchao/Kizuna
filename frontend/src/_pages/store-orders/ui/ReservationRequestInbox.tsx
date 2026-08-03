'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'react-hot-toast';
import { Order, orderApi } from '@/entities/order';
import { getApiErrorMessage } from '@/shared/lib';
import { Badge, Button } from '@/shared/ui';

interface ReservationRequestInboxProps {
  /** 確定・謝絶の成功後に呼ばれる（同じ受注の状態が変わるため、一覧の再取得に使う）。 */
  onProcessed: () => void;
}

/** 1 回に読み込む件数。未処理の申請は処理し終えるまで残り続けるため、取得は上限で抑えて追加読み込みで辿る。 */
const PAGE_SIZE = 20;

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
  // 表示できるものが何も無い状態での取得失敗。空表示（＝申請なし）と区別する。
  const [failed, setFailed] = useState(false);
  // 表示中の申請を保ったまま失敗した取得の対象ページ数（再試行にそのまま使う）。
  const [failedPages, setFailedPages] = useState<number | null>(null);
  const [processingId, setProcessingId] = useState<string | null>(null);
  // 読み込み済みのページ数。確定・謝絶後の取り直しでも同じ範囲を保つために持つ。
  const [loadedPages, setLoadedPages] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  // 失敗時の分岐に使う「今表示している件数」。load は再生成しない（mount 用 effect を引き直さない）ため
  // state を読まずに済ませる。
  const shownCount = useRef(0);

  const load = useCallback((pages: number) => {
    setLoading(true);
    setFailedPages(null);
    // 再読み込み中は失敗表示を畳む。残したままだと、押した再読み込みが効いているのか分からない。
    setFailed(false);
    // 処理後も表示中の範囲を保つため、読み込み済みページ分をまとめて取り直す。
    // ページ単位で継ぎ足すと、処理で 1 件消えた分だけ後続が繰り上がり、境界の申請を飛ばす。
    const size = pages * PAGE_SIZE;
    orderApi
      .listReservationRequests({ page: 0, size })
      .then(page => {
        shownCount.current = page.rows.length;
        setRequests(page.rows);
        // 要求した件数より少なく返ってきたのに残りがある＝サーバ側の上限に当たっている。
        // ここで「もっと見る」を出し続けると、押しても増えないボタンになる。
        setHasMore(page.rows.length < page.total && page.rows.length >= size);
        setLoadedPages(pages);
      })
      .catch(() => {
        // 表示中の申請があるなら消さない — 追加読み込みの失敗で既読み込み分まで消すと、
        // 見えていた未処理の申請を見失う。読み込み済みページ数も進めない。
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
      load(loadedPages);
      onProcessed();
    } catch (error) {
      // 指名の再検証など、サーバは対処方法（修正か謝絶か）を含む文言を返す。汎用文言に潰さない。
      toast.error(
        getApiErrorMessage(
          error,
          action === 'confirm' ? '確定に失敗しました' : '謝絶に失敗しました'
        )
      );
    } finally {
      setProcessingId(null);
    }
  };

  if (loading && requests.length === 0) {
    return <p className="text-sm text-muted-foreground">読み込み中...</p>;
  }
  // 取得失敗を空表示と区別する — 「申請なし」に見せると未処理の申請を見落とす
  if (failed) {
    return (
      <div className="flex items-center gap-3">
        <p className="text-sm text-destructive-strong">予約申請を取得できませんでした。</p>
        <Button type="button" variant="outline" size="sm" onClick={() => load(loadedPages)}>
          再読み込み
        </Button>
      </div>
    );
  }
  if (requests.length === 0) {
    return <p className="text-sm text-muted-foreground">未確定の予約申請はありません</p>;
  }

  return (
    <>
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
      {failedPages !== null && (
        <div className="mt-3 flex items-center gap-3">
          <p className="text-sm text-destructive-strong">
            予約申請を取得できませんでした。表示は前回の取得内容です。
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={loading}
            onClick={() => load(failedPages)}
          >
            再試行
          </Button>
        </div>
      )}
      {hasMore && failedPages === null && (
        <Button
          type="button"
          variant="outline"
          className="mt-3 w-full"
          disabled={loading}
          onClick={() => load(loadedPages + 1)}
        >
          もっと見る
        </Button>
      )}
    </>
  );
}
