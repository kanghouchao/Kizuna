'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { Order, orderApi } from '@/entities/order';
import { getApiErrorMessage } from '@/shared/lib';
import { Badge, Button } from '@/shared/ui';
import { ReservationRequestEditModal } from './ReservationRequestEditModal';

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
  // 表示中の申請を保ったまま失敗した追加読み込み。再試行は同じ続きの位置をそのまま使う。
  const [loadMoreFailed, setLoadMoreFailed] = useState(false);
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [editing, setEditing] = useState<Order | null>(null);
  // 続きの位置。null なら続きが無い（サーバが返した位置をそのまま持ち、表示中の行からは作らない —
  // 最後に表示していた申請が処理で消えても、続きの位置は失われてはならない）。
  const [nextCursor, setNextCursor] = useState<string | null>(null);

  const load = useCallback((cursor: string | null) => {
    setLoading(true);
    setLoadMoreFailed(false);
    // 再読み込み中は失敗表示を畳む。残したままだと、押した再読み込みが効いているのか分からない。
    setFailed(false);
    // 1 回の取得は常に PAGE_SIZE 件で、続きは位置（カーソル）を渡して 1 回ずつ継ぎ足す。
    // 要求サイズ自体を膨らませる形にすると、サーバ側の取得上限に当たった時点で
    // それ以降の申請へ到達する手段が無くなる。
    orderApi
      .listReservationRequests({ cursor: cursor ?? undefined, size: PAGE_SIZE })
      .then(page => {
        // 続きは継ぎ足す。位置は並びの鍵なので、確定・謝絶で手前の行が消えても後続が繰り上がらず、
        // 読み込み済みの範囲を読み直さなくても境界の申請を飛ばさない。
        setRequests(prev => (cursor === null ? page.rows : [...prev, ...page.rows]));
        setNextCursor(page.nextCursor);
      })
      .catch(() => {
        // 表示中の申請があるなら消さない — 追加読み込みの失敗で既読み込み分まで消すと、
        // 見えていた未処理の申請を見失う。続きの位置も進めない。
        if (cursor === null) {
          setFailed(true);
        } else {
          setLoadMoreFailed(true);
        }
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load(null);
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
      // 処理し終えた申請は inbox の対象から外れるので、手元から取り除くだけで一覧は正しくなる。
      // 取り直しに行くと、その 1 回のために読み込み済みの範囲ぶんの要求を撒くことになる。
      setRequests(prev => prev.filter(request => request.id !== id));
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
        <Button type="button" variant="outline" size="sm" onClick={() => load(null)}>
          再読み込み
        </Button>
      </div>
    );
  }
  // 続きが残っているうちは「申請なし」と言い切らない — 表示中をすべて処理し終えただけで、
  // まだ読んでいない申請がある状態と区別できなくなる。
  if (requests.length === 0 && nextCursor === null) {
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
            {/* 処理の結果が返るまで同じ行を押せると、済んだ遷移をもう一度投げてしまう。 */}
            <div className="flex gap-2">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setEditing(request)}
                disabled={loading || processingId === request.id}
              >
                編集
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => process(request.id ?? '', 'decline')}
                disabled={loading || processingId === request.id}
              >
                謝絶
              </Button>
              <Button
                type="button"
                size="sm"
                onClick={() => process(request.id ?? '', 'confirm')}
                disabled={loading || processingId === request.id}
              >
                確定
              </Button>
            </div>
          </li>
        ))}
      </ul>
      {loadMoreFailed && (
        <div className="mt-3 flex items-center gap-3">
          <p className="text-sm text-destructive-strong">
            予約申請を取得できませんでした。表示は前回の取得内容です。
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={loading}
            onClick={() => load(nextCursor)}
          >
            再試行
          </Button>
        </div>
      )}
      {nextCursor !== null && !loadMoreFailed && (
        <Button
          type="button"
          variant="outline"
          className="mt-3 w-full"
          disabled={loading}
          onClick={() => load(nextCursor)}
        >
          もっと見る
        </Button>
      )}
      <ReservationRequestEditModal
        request={editing}
        onClose={() => setEditing(null)}
        // 編集後も申請は未確定のまま inbox に残るので、その行だけ差し替える
        onSaved={updated => {
          setRequests(prev => prev.map(request => (request.id === updated.id ? updated : request)));
          onProcessed();
        }}
      />
    </>
  );
}
