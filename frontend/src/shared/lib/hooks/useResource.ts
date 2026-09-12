'use client';

import { Dispatch, SetStateAction, useCallback, useEffect, useRef, useState } from 'react';
import { createResourceRequest, ResourceFailure } from './resourceRequest';

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

/** 単一値の取得。無効化中も保持値を残し、取得条件は deps で指定する。 */
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
  const [request] = useState(createResourceRequest);
  const [data, setData] = useState<T | null>(null);
  const [isLoading, setIsLoading] = useState(fetcher !== null);
  const [failure, setFailure] = useState<ResourceFailure | null>(null);

  const load = useCallback(async () => {
    const currentFetcher = fetcherRef.current;
    // 在途のリクエストは、これから取りに行くかどうかに関わらず無効化する
    request.invalidate();
    // 失敗も同じく、取りに行くかどうかに関わらず畳む。再取得中に残すと押した再試行が効いて
    // いるのか分からず、取りに行かなくなった後に残すと、押しても何も起きない再試行が出たまま
    // になる（値と違って、前の失敗が今の状態を説明することは無い）。
    setFailure(null);
    if (currentFetcher === null) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    const result = await request.run(currentFetcher);
    if (result.status === 'stale' || !result.isCurrent()) return;
    setData(result.status === 'success' ? result.data : null);
    setFailure(result.status === 'success' ? null : result.status);
    setIsLoading(false);
  }, [request]);

  useEffect(() => {
    void load();
    return () => {
      request.invalidate();
    };
    // fetcher は ref 越しに最新を読むので依存に載せない。取り直しの契機は deps だけ
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load, ...deps]);

  return { data, setData, isLoading, failure, reload: load };
}
