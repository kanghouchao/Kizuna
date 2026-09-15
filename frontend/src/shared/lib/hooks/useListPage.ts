'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { PageResult } from '@/shared/api';

const EMPTY_PAGE: PageResult<never> = { rows: [], page: 0, pageCount: 0, total: 0 };

interface ListPageResult<T, C> extends PageResult<T> {
  isLoading: boolean;
  /** 取得に失敗した状態。行が無いだけの空表示（＝0 件）と区別するために分ける。 */
  failed: boolean;
  /** 最新要求の失敗原因。分類と表示は呼び出し側が決める。 */
  error: unknown;
  /** 検索条件を適用して 1 ページ目から取り直す（関数形は適用済み条件からの差分更新） */
  search: (criteria: C | ((prev: C) => C)) => Promise<void>;
  onPageChange: (page: number) => Promise<void>;
  reload: () => Promise<void>;
}

/**
 * ListPage のページ・適用済み検索条件・失敗を管理し、最新要求の結果だけを反映する。
 * 検索条件は引数で fetcher に渡し、条件更新直後の古いクロージャによる取得を防ぐ。
 * 失敗は failed と error で返し、分類と提示は呼び出し側に委ねる。
 */
export function useListPage<T>(
  fetcher: (page: number) => Promise<PageResult<T>>
): ListPageResult<T, void>;
export function useListPage<T, C>(
  fetcher: (page: number, criteria: C) => Promise<PageResult<T>>,
  initialCriteria: C
): ListPageResult<T, C>;
export function useListPage<T, C>(
  fetcher: (page: number, criteria: C) => Promise<PageResult<T>>,
  initialCriteria?: C
): ListPageResult<T, C> {
  const fetcherRef = useRef(fetcher);
  // レンダー中の ref 書き込みは不可のため、コミット後に最新のクロージャへ差し替える
  useEffect(() => {
    fetcherRef.current = fetcher;
  });
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する
  const requestIdRef = useRef(0);
  // 適用済みの検索条件。ページ送りと再取得はこれをそのまま使う
  const criteriaRef = useRef(initialCriteria as C);
  const [pageResult, setPageResult] = useState<PageResult<T>>(EMPTY_PAGE);
  const [isLoading, setIsLoading] = useState(true);
  const [failure, setFailure] = useState<{ error: unknown } | null>(null);

  const load = useCallback(async (page: number) => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    // 再取得中は失敗表示を畳む。残したままだと、押した再試行が効いているのか分からない。
    setFailure(null);
    try {
      const result = await fetcherRef.current(page, criteriaRef.current);
      if (requestId === requestIdRef.current) setPageResult(result);
    } catch (error) {
      // 行も現在ページも起点へ戻す。位置を残すと再試行が 21〜40 行目だけの欠番一覧を返す。
      // 適用済みの検索条件は利用者の指定なので保つ（戻すのは位置だけ）。
      if (requestId === requestIdRef.current) {
        setPageResult(EMPTY_PAGE);
        setFailure({ error });
      }
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(0);
    return () => {
      // requestIdRef はリクエストカウンタであり DOM ref ではない
      // eslint-disable-next-line react-hooks/exhaustive-deps
      requestIdRef.current++;
    };
  }, [load]);

  // 検索条件を適用して 1 ページ目から取り直す。関数形は setState と同様、適用済み条件を基に
  // した差分更新。呼び出し側の入力 state（未提出の下書き）を読んではいけない背景処理が、
  // 条件の一部だけを差し替えるための形。
  const search = useCallback(
    (criteria: C | ((prev: C) => C)) => {
      criteriaRef.current =
        typeof criteria === 'function'
          ? (criteria as (prev: C) => C)(criteriaRef.current)
          : criteria;
      return load(0);
    },
    [load]
  );
  const onPageChange = useCallback((page: number) => load(page), [load]);
  // 削除・発行など一覧を書き換える操作の後始末。現在のページをそのまま取り直す。
  // 失敗時は現在ページが 0 に戻っているので、同じ関数がそのまま失敗の再試行にもなる。
  const reload = useCallback(() => load(pageResult.page), [load, pageResult.page]);

  return {
    ...pageResult,
    isLoading,
    failed: failure !== null,
    error: failure?.error,
    search,
    onPageChange,
    reload,
  };
}
