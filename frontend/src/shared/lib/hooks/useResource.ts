'use client';

import { Dispatch, SetStateAction, useCallback, useEffect, useRef, useState } from 'react';
import { isNotFound } from '../apiError';

/**
 * 取得の失敗。404 は何度押しても取れないため、再試行を出せる失敗と分けて渡す
 * （提示の形が違う — DESIGN.md「領域内エラー態」）。
 */
type ResourceFailure = 'notFound' | 'error';

interface ResourceResult<T> {
  /** 取得できていないとき（未取得・失敗）は null。 */
  data: T | null;
  /**
   * 更新の応答をそのまま反映するための差し替え口。取り直しに倒すと、直したばかりの内容が
   * 読み込み表示で一瞬消える。
   */
  setData: Dispatch<SetStateAction<T | null>>;
  isLoading: boolean;
  failure: ResourceFailure | null;
  /** 取り直す。失敗の再試行もこれ。 */
  reload: () => Promise<void>;
}

/**
 * 単一リソース（詳細 1 件・選択肢・統計）の取得ライフサイクル
 * （初回取得 / loading / 失敗の分類 / 再取得 / 順不同レスポンス守衛）。
 * fetcher は毎レンダー最新のクロージャを参照するため、state をそのまま閉じ込めてよい。
 * 取得パラメータ（詳細頁の id 等）は deps に列挙する — 変わると取り直す。
 * deps は毎レンダー同じ長さで渡すこと（React が依存配列の長さの変化を許さない）。
 *
 * <p>fetcher に null を渡す間は取りに行かない。閉じたモーダルのように、まだ取る理由が無い
 * 場面のための形。無効化は「取りに行かない」であって「読めていたものを忘れる」ではないので、
 * 持っている値はそのまま残す（在途のリクエストだけ無効化する）。
 *
 * <p>失敗は failure で伝えるだけで、提示は行わない。取得に失敗した領域が自分で名乗るのか
 * 通知に出すのかは呼び出し側の場面が決めることで、フックの内側に隠れてはならない。
 *
 * <p>既知の限界：読み込み中の表示は 1 コミット遅れる。deps の変化と fetcher の有効化は
 * どちらもそのレンダーを終えた効果で取得を始めるため、切り替わったフレームは取得前の姿
 * （data 無し・isLoading false）で一度描かれる。常時 mount のモーダルを開いた最初のフレームが
 * これに当たる。
 */
export function useResource<T>(
  fetcher: (() => Promise<T>) | null,
  deps: unknown[] = []
): ResourceResult<T> {
  const fetcherRef = useRef(fetcher);
  // レンダー中の ref 書き込みは不可のため、コミット後に最新のクロージャへ差し替える
  useEffect(() => {
    fetcherRef.current = fetcher;
  });
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する
  const requestIdRef = useRef(0);
  const [data, setData] = useState<T | null>(null);
  const [isLoading, setIsLoading] = useState(fetcher !== null);
  const [failure, setFailure] = useState<ResourceFailure | null>(null);

  const load = useCallback(async () => {
    const currentFetcher = fetcherRef.current;
    // 在途のリクエストは、これから取りに行くかどうかに関わらず無効化する
    const requestId = ++requestIdRef.current;
    if (currentFetcher === null) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    // 再取得中は失敗表示を畳む。残したままだと、押した再試行が効いているのか分からない。
    setFailure(null);
    try {
      const result = await currentFetcher();
      if (requestId === requestIdRef.current) setData(result);
    } catch (error) {
      // 読めなかった値は残さない — 領域に前回の内容が居座ると、それが最新に見える
      if (requestId === requestIdRef.current) {
        setData(null);
        setFailure(isNotFound(error) ? 'notFound' : 'error');
      }
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    return () => {
      // requestIdRef はリクエストカウンタであり DOM ref ではない
      // eslint-disable-next-line react-hooks/exhaustive-deps
      requestIdRef.current++;
    };
    // fetcher は ref 越しに最新を読むので依存に載せない。取り直しの契機は deps だけ
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load, ...deps]);

  return { data, setData, isLoading, failure, reload: load };
}
