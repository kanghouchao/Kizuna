'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'react-hot-toast';

/**
 * 管理系一覧ページ共通の取得ライフサイクル（初回取得 / loading / 失敗トースト / 再取得）。
 * fetcher は毎レンダー最新のクロージャを参照するため、検索条件の state をそのまま閉じ込めてよい。
 * テーブルの markup は各ページの責務（汎用テーブル化はしない）。
 */
export function useManagedList<T>(fetcher: () => Promise<T[]>, errorMessage: string) {
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する
  const requestIdRef = useRef(0);
  const [items, setItems] = useState<T[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const refetch = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    try {
      const result = await fetcherRef.current();
      if (requestId === requestIdRef.current) setItems(result);
    } catch {
      if (requestId === requestIdRef.current) toast.error(errorMessage);
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, [errorMessage]);

  useEffect(() => {
    void refetch();
    return () => {
      // requestIdRef is a request counter, not a DOM ref.
      // eslint-disable-next-line react-hooks/exhaustive-deps
      requestIdRef.current++;
    };
  }, [refetch]);

  return { items, isLoading, refetch };
}
