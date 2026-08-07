'use client';

import { Dispatch, SetStateAction, useCallback, useEffect, useRef, useState } from 'react';
import { CursorPageResult } from '@/shared/api';

interface CursorListResult<T> {
  rows: T[];
  /** 処理を終えた行の扱い（取り除くか差し替えるか）は一覧ごとに違うため、呼出側に委ねる。 */
  setRows: Dispatch<SetStateAction<T[]>>;
  isLoading: boolean;
  /** 取得に失敗した状態。行が無いだけの空表示（＝0 件）と区別するために分ける。 */
  failed: boolean;
  hasMore: boolean;
  /** 先頭から読み直す。失敗の再試行もこれ。 */
  reload: () => void;
  /** 続きを 1 回分だけ継ぎ足す。 */
  loadMore: () => void;
}

/**
 * 行が処理で消えていく作業キュー型一覧の取得ライフサイクル（先頭取得 / 追加読み込み / 失敗）。
 *
 * <p>位置は「何件目か」ではなくサーバが返したカーソルを持ち回る。件数で持つと、手前の行が処理で消えた分だけ
 * 後続が繰り上がり、続きを取った時点で境界の行を飛ばす。カーソルを表示中の行から作らないのも同じ理由で、
 * 最後に表示していた行が処理で消えても続きの位置は失われてはならない。
 *
 * <p>1 回の取得は常に 1 要求。読み込み済みの範囲を読み直さないため、要求数は読み込んだ量に依らない。
 */
export function useCursorList<T>(
  fetcher: (cursor?: string) => Promise<CursorPageResult<T>>
): CursorListResult<T> {
  const fetcherRef = useRef(fetcher);
  // レンダー中の ref 書き込みは不可のため、コミット後に最新のクロージャへ差し替える
  useEffect(() => {
    fetcherRef.current = fetcher;
  });
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する
  const requestIdRef = useRef(0);

  const [rows, setRows] = useState<T[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);

  const load = useCallback((cursor: string | null) => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    // 再読み込み中は失敗表示を畳む。残したままだと、押した再読み込みが効いているのか分からない。
    setFailed(false);
    fetcherRef
      .current(cursor ?? undefined)
      .then(page => {
        if (requestId !== requestIdRef.current) return;
        setRows(prev => (cursor === null ? page.rows : [...prev, ...page.rows]));
        setNextCursor(page.nextCursor);
      })
      .catch(() => {
        if (requestId !== requestIdRef.current) return;
        // 追加読み込みの失敗も先頭取得と同じ扱いにする。行と位置の両方を起点へ戻すのは、
        // 途中の位置を残したまま再試行すると 1〜20 行目を欠いた 21 行目以降だけが返るため。
        setRows([]);
        setNextCursor(null);
        setFailed(true);
      })
      .finally(() => {
        if (requestId === requestIdRef.current) setIsLoading(false);
      });
  }, []);

  useEffect(() => {
    load(null);
    return () => {
      // requestIdRef はリクエストカウンタであり DOM ref ではない
      // eslint-disable-next-line react-hooks/exhaustive-deps
      requestIdRef.current++;
    };
  }, [load]);

  return {
    rows,
    setRows,
    isLoading,
    failed,
    hasMore: nextCursor !== null,
    reload: useCallback(() => load(null), [load]),
    loadMore: useCallback(() => load(nextCursor), [load, nextCursor]),
  };
}
