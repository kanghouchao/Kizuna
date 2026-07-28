'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'react-hot-toast';
import { PageResult } from '@/shared/api';

const EMPTY_PAGE: PageResult<never> = { rows: [], page: 0, pageCount: 0, total: 0 };

/**
 * ListPage 向けの取得ライフサイクル（page state・失敗トースト・順不同レスポンス守衛）。
 * fetcher は毎レンダー最新のクロージャを参照するため、検索条件の state は useManagedList 同様に
 * 呼び出し側がそのまま閉じ込めてよい。hook が持つのはページ番号（0 起点）だけ。
 */
export function useListPage<T>(
  fetcher: (page: number) => Promise<PageResult<T>>,
  errorMessage: string
) {
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する
  const requestIdRef = useRef(0);
  const [pageResult, setPageResult] = useState<PageResult<T>>(EMPTY_PAGE);
  const [isLoading, setIsLoading] = useState(true);

  const load = useCallback(
    async (page: number) => {
      const requestId = ++requestIdRef.current;
      setIsLoading(true);
      try {
        const result = await fetcherRef.current(page);
        if (requestId === requestIdRef.current) setPageResult(result);
      } catch {
        if (requestId === requestIdRef.current) toast.error(errorMessage);
      } finally {
        if (requestId === requestIdRef.current) setIsLoading(false);
      }
    },
    [errorMessage]
  );

  useEffect(() => {
    void load(0);
    return () => {
      // requestIdRef はリクエストカウンタであり DOM ref ではない
      // eslint-disable-next-line react-hooks/exhaustive-deps
      requestIdRef.current++;
    };
  }, [load]);

  // 検索条件が変わったときの再取得は 1 ページ目に戻す。
  // fetcher クロージャは直近のレンダーの値を参照するため、検索条件の state を更新した
  // 同一ハンドラ内で続けて呼ぶと再レンダー前の古い条件で取得してしまう
  // （useManagedList の refetch と同じ制約）。state 更新の反映後（次のレンダー以降）に呼ぶこと。
  const search = useCallback(() => load(0), [load]);
  const onPageChange = useCallback((page: number) => load(page), [load]);
  // 削除・発行など一覧を書き換える操作の後始末。現在のページをそのまま取り直す
  const reload = useCallback(() => load(pageResult.page), [load, pageResult.page]);

  return { ...pageResult, isLoading, search, onPageChange, reload };
}
