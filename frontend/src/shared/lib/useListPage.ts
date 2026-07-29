'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'react-hot-toast';
import { PageResult } from '@/shared/api';

const EMPTY_PAGE: PageResult<never> = { rows: [], page: 0, pageCount: 0, total: 0 };

interface ListPageState<T, C> extends PageResult<T> {
  isLoading: boolean;
  /** 検索条件を適用して 1 ページ目から取り直す */
  search: (criteria: C) => Promise<void>;
  onPageChange: (page: number) => Promise<void>;
  reload: () => Promise<void>;
}

/**
 * ListPage 向けの取得ライフサイクル（page・適用済み検索条件・失敗トースト・順不同レスポンス守衛）。
 * 検索条件は fetcher の引数として渡す。呼び出し側の state を fetcher のクロージャから読ませると、
 * 条件を更新した同一ハンドラ内で再取得したとき再レンダー前の古い値で取得してしまうため、
 * 「どの条件で取得するか」は hook が持つ。検索条件を持たない一覧は C を省略してよい。
 */
export function useListPage<T>(
  fetcher: (page: number) => Promise<PageResult<T>>,
  errorMessage: string
): ListPageState<T, void>;
export function useListPage<T, C>(
  fetcher: (page: number, criteria: C) => Promise<PageResult<T>>,
  errorMessage: string,
  initialCriteria: C
): ListPageState<T, C>;
export function useListPage<T, C>(
  fetcher: (page: number, criteria: C) => Promise<PageResult<T>>,
  errorMessage: string,
  initialCriteria?: C
): ListPageState<T, C> {
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する
  const requestIdRef = useRef(0);
  // 適用済みの検索条件。ページ送りと再取得はこれをそのまま使う
  const criteriaRef = useRef(initialCriteria as C);
  const [pageResult, setPageResult] = useState<PageResult<T>>(EMPTY_PAGE);
  const [isLoading, setIsLoading] = useState(true);

  const load = useCallback(
    async (page: number) => {
      const requestId = ++requestIdRef.current;
      setIsLoading(true);
      try {
        const result = await fetcherRef.current(page, criteriaRef.current);
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

  // 検索条件を適用して 1 ページ目から取り直す
  const search = useCallback(
    (criteria: C) => {
      criteriaRef.current = criteria;
      return load(0);
    },
    [load]
  );
  const onPageChange = useCallback((page: number) => load(page), [load]);
  // 削除・発行など一覧を書き換える操作の後始末。現在のページをそのまま取り直す
  const reload = useCallback(() => load(pageResult.page), [load, pageResult.page]);

  return { ...pageResult, isLoading, search, onPageChange, reload };
}
