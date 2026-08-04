'use client';

import { Dispatch, SetStateAction, useCallback, useEffect, useRef, useState } from 'react';
import { CursorPageResult } from '@/shared/api';

interface CursorListResult<T> {
  rows: T[];
  /** 処理を終えた行の扱い（取り除くか差し替えるか）は一覧ごとに違うため、呼出側に委ねる。 */
  setRows: Dispatch<SetStateAction<T[]>>;
  isLoading: boolean;
  /** 表示できるものが何も無い状態での取得失敗。空表示（＝0 件）と区別するために分ける。 */
  failed: boolean;
  /** 表示中の行を保ったまま失敗した追加読み込み。 */
  loadMoreFailed: boolean;
  hasMore: boolean;
  /** 先頭から読み直す。 */
  reload: () => void;
  /** 続きを 1 回分だけ継ぎ足す。失敗した追加読み込みの再試行も同じ位置から行う。 */
  loadMore: () => void;
}

/**
 * 行が処理で消えていく作業キュー型一覧の取得ライフサイクル（先頭取得 / 追加読み込み / 失敗の二段構え）。
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

  const [rows, setRows] = useState<T[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [loadMoreFailed, setLoadMoreFailed] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);

  const load = useCallback((cursor: string | null) => {
    setIsLoading(true);
    setLoadMoreFailed(false);
    // 再読み込み中は失敗表示を畳む。残したままだと、押した再読み込みが効いているのか分からない。
    setFailed(false);
    fetcherRef
      .current(cursor ?? undefined)
      .then(page => {
        setRows(prev => (cursor === null ? page.rows : [...prev, ...page.rows]));
        setNextCursor(page.nextCursor);
      })
      .catch(() => {
        // 表示中の行があるなら消さない — 追加読み込みの失敗で既読み込み分まで失うと、見えていた行を見失う。
        // 続きの位置も進めないので、再試行は同じ位置から続けられる。
        if (cursor === null) {
          setFailed(true);
        } else {
          setLoadMoreFailed(true);
        }
      })
      .finally(() => setIsLoading(false));
  }, []);

  useEffect(() => {
    load(null);
  }, [load]);

  return {
    rows,
    setRows,
    isLoading,
    failed,
    loadMoreFailed,
    hasMore: nextCursor !== null,
    reload: useCallback(() => load(null), [load]),
    loadMore: useCallback(() => load(nextCursor), [load, nextCursor]),
  };
}
