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
  const [items, setItems] = useState<T[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const refetch = useCallback(async () => {
    try {
      setIsLoading(true);
      setItems(await fetcherRef.current());
    } catch {
      toast.error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  }, [errorMessage]);

  useEffect(() => {
    void refetch();
  }, [refetch]);

  return { items, isLoading, refetch };
}
