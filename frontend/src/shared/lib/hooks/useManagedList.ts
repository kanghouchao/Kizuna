'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * 管理系一覧ページ共通の取得ライフサイクル（初回取得 / loading / 失敗 / 再取得）。
 * fetcher は毎レンダー最新のクロージャを参照するため、検索条件の state をそのまま閉じ込めてよい。
 * テーブルの markup は各ページの責務（汎用テーブル化はしない）。
 *
 * <p>失敗は failed で伝えるだけで、提示は行わない。取得に失敗した領域が自分で名乗るのか
 * 通知に出すのかは呼び出し側の場面が決めることで、フックの内側に隠れてはならない。
 */
export function useManagedList<T>(fetcher: () => Promise<T[]>) {
  const fetcherRef = useRef(fetcher);
  // レンダー中の ref 書き込みは不可のため、コミット後に最新のクロージャへ差し替える
  useEffect(() => {
    fetcherRef.current = fetcher;
  });
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する
  const requestIdRef = useRef(0);
  const [items, setItems] = useState<T[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const refetch = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    // 再取得中は失敗表示を畳む。残したままだと、押した再試行が効いているのか分からない。
    setFailed(false);
    try {
      const result = await fetcherRef.current();
      if (requestId === requestIdRef.current) setItems(result);
    } catch {
      // 古い項目は残さない — 読めなかった領域に前回の内容が居座ると、それが最新に見える
      if (requestId === requestIdRef.current) {
        setItems([]);
        setFailed(true);
      }
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void refetch();
    return () => {
      // requestIdRef is a request counter, not a DOM ref.
      // eslint-disable-next-line react-hooks/exhaustive-deps
      requestIdRef.current++;
    };
  }, [refetch]);

  return { items, isLoading, failed, refetch };
}
