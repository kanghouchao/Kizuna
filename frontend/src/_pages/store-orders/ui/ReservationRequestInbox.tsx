'use client';

import { useState } from 'react';
import { notify } from '@/shared/notify';
import { Order, orderApi } from '@/entities/order';
import { getApiErrorMessage, useCursorList } from '@/shared/lib';
import { Badge, Button, RegionError } from '@/shared/ui';
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
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [editing, setEditing] = useState<Order | null>(null);
  // 1 回の取得は常に PAGE_SIZE 件で、続きは位置（カーソル）を渡して 1 回ずつ継ぎ足す。要求サイズ自体を
  // 膨らませる形にすると、サーバ側の取得上限に当たった時点でそれ以降の申請へ到達する手段が無くなる。
  const {
    rows: requests,
    setRows: setRequests,
    isLoading: loading,
    failed,
    hasMore,
    reload,
    loadMore,
  } = useCursorList<Order>(cursor => orderApi.listReservationRequests({ cursor, size: PAGE_SIZE }));

  const process = async (id: string, action: 'confirm' | 'decline') => {
    setProcessingId(id);
    try {
      if (action === 'confirm') {
        await orderApi.confirm(id);
        notify.success('予約を確定しました');
      } else {
        await orderApi.decline(id);
        notify.success('予約を謝絶しました');
      }
      // 処理し終えた申請は inbox の対象から外れるので、手元から取り除くだけで一覧は正しくなる。
      // 取り直しに行くと、その 1 回のために読み込み済みの範囲ぶんの要求を撒くことになる。
      setRequests(prev => prev.filter(request => request.id !== id));
      onProcessed();
    } catch (error) {
      // 指名の再検証など、サーバは対処方法（修正か謝絶か）を含む文言を返す。汎用文言に潰さない。
      notify.error(
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
    return <RegionError message="予約申請を取得できませんでした。" onRetry={reload} />;
  }
  // 続きが残っているうちは「申請なし」と言い切らない — 表示中をすべて処理し終えただけで、
  // まだ読んでいない申請がある状態と区別できなくなる。
  if (requests.length === 0 && !hasMore) {
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
      {hasMore && (
        <Button
          type="button"
          variant="outline"
          className="mt-3 w-full"
          disabled={loading}
          onClick={loadMore}
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
