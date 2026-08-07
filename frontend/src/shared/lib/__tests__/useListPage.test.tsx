import { useState } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { useListPage } from '@/shared/lib';
import { PageResult } from '@/shared/api';

const pageOf = (page: number, rows: string[] = ['a']): PageResult<string> => ({
  rows,
  page,
  pageCount: 3,
  total: 21,
});

describe('useListPage', () => {
  it('マウント時に 0 ページ目を取得し pageResult と isLoading を管理する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page, ['a', 'b']));
    const { result } = renderHook(() => useListPage(fetcher));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.rows).toEqual(['a', 'b']);
    expect(result.current.page).toBe(0);
    expect(fetcher).toHaveBeenCalledWith(0, undefined);
  });

  it('onPageChange は指定ページで再取得する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() => useListPage(fetcher));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.onPageChange(2);
    });

    expect(fetcher).toHaveBeenLastCalledWith(2, undefined);
    expect(result.current.page).toBe(2);
  });

  it('reload は現在のページのまま再取得する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() => useListPage(fetcher));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.onPageChange(2);
    });

    await act(async () => {
      await result.current.reload();
    });

    expect(fetcher).toHaveBeenLastCalledWith(2, undefined);
    expect(result.current.page).toBe(2);
  });

  it('search は現在ページに関わらず 0 ページ目へ戻して再取得する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() => useListPage(fetcher));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.onPageChange(2);
    });
    expect(result.current.page).toBe(2);

    await act(async () => {
      await result.current.search();
    });

    expect(fetcher).toHaveBeenLastCalledWith(0, undefined);
    expect(result.current.page).toBe(0);
  });

  it('初回取得には初期条件を使う', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    renderHook(() => useListPage<string, string>(fetcher, 'あ'));

    await waitFor(() => expect(fetcher).toHaveBeenCalledWith(0, 'あ'));
  });

  it('search に渡した条件はページ送り・再取得にも引き継がれる', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() => useListPage<string, string>(fetcher, ''));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.search('やまだ');
    });
    expect(fetcher).toHaveBeenLastCalledWith(0, 'やまだ');

    await act(async () => {
      await result.current.onPageChange(1);
    });
    expect(fetcher).toHaveBeenLastCalledWith(1, 'やまだ');

    await act(async () => {
      await result.current.reload();
    });
    expect(fetcher).toHaveBeenLastCalledWith(1, 'やまだ');
  });

  // 背景処理（呼び出し側の入力 state を読んではいけない文脈）が条件の一部だけを差し替えるための形
  it('search の関数形は適用済み条件を基にした差分更新で取得する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() =>
      useListPage<string, { term: string; storeId?: number }>(fetcher, { term: '' })
    );
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.search({ term: 'やまだ', storeId: 9 });
    });

    await act(async () => {
      await result.current.search(prev => ({ ...prev, storeId: undefined }));
    });

    expect(fetcher).toHaveBeenLastCalledWith(0, { term: 'やまだ', storeId: undefined });
  });

  it('条件を保持する state を更新した直後に呼んでも、渡した条件で取得する', async () => {
    // 呼び出し側の state を fetcher のクロージャから読ませていた頃は、条件を更新した同一
    // ハンドラ内で search を呼ぶと再レンダー前の古い値で取得していた。条件は引数で渡す。
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() => {
      const [term, setTerm] = useState('');
      const list = useListPage<string, string>(fetcher, '');
      return { list, term, setTerm };
    });
    await waitFor(() => expect(result.current.list.isLoading).toBe(false));

    await act(async () => {
      result.current.setTerm('あたらしい');
      await result.current.list.search('あたらしい');
    });

    expect(fetcher).toHaveBeenLastCalledWith(0, 'あたらしい');
  });

  it('古いリクエストの遅延応答は新しい結果を上書きしない', async () => {
    const resolvers: Array<(value: PageResult<string>) => void> = [];
    const fetcher = jest.fn(
      () => new Promise<PageResult<string>>(resolve => resolvers.push(resolve))
    );
    const { result } = renderHook(() => useListPage(fetcher));

    act(() => {
      void result.current.onPageChange(1);
    });
    await act(async () => {
      resolvers[1](pageOf(1, ['newer']));
    });
    await act(async () => {
      resolvers[0](pageOf(0, ['stale']));
    });

    expect(result.current.rows).toEqual(['newer']);
    expect(result.current.isLoading).toBe(false);
  });

  it('古いリクエストの遅延失敗は新しい結果を消さない', async () => {
    // 失敗が rows を空にするようになったため、守衛を落とすと在途の古い失敗が
    // 新しく届いたページを丸ごと消し、領域をエラー態へ倒してしまう
    const settlers: Array<{
      resolve: (value: PageResult<string>) => void;
      reject: (reason?: unknown) => void;
    }> = [];
    const fetcher = jest.fn(
      () => new Promise<PageResult<string>>((resolve, reject) => settlers.push({ resolve, reject }))
    );
    const { result } = renderHook(() => useListPage(fetcher));

    act(() => {
      void result.current.onPageChange(1);
    });
    await act(async () => {
      settlers[1].resolve(pageOf(1, ['newer']));
    });
    await act(async () => {
      settlers[0].reject(new Error('boom'));
    });

    expect(result.current.rows).toEqual(['newer']);
    expect(result.current.failed).toBe(false);
  });

  it('失敗時は failed を立てて行と現在ページを起点へ戻す', async () => {
    // 古い行を残すと「読めなかった」が「これが最新」に化ける
    let succeed = true;
    const fetcher = jest.fn(async (page: number) => {
      if (!succeed) throw new Error('boom');
      return pageOf(page);
    });
    const { result } = renderHook(() => useListPage(fetcher));
    await act(async () => {
      await result.current.onPageChange(2);
    });

    succeed = false;
    await act(async () => {
      await result.current.reload();
    });

    expect(result.current.failed).toBe(true);
    expect(result.current.rows).toEqual([]);
    expect(result.current.page).toBe(0);
    expect(result.current.isLoading).toBe(false);
  });

  it('失敗後の再取得は 1 ページ目から取り直す', async () => {
    // 失敗したページから再開すると、21〜40 行目だけが返る欠番だらけの一覧になる
    let succeed = true;
    const fetcher = jest.fn(async (page: number) => {
      if (!succeed) throw new Error('boom');
      return pageOf(page);
    });
    const { result } = renderHook(() => useListPage(fetcher));
    await act(async () => {
      await result.current.onPageChange(2);
    });

    succeed = false;
    await act(async () => {
      await result.current.reload();
    });
    succeed = true;
    await act(async () => {
      await result.current.reload();
    });

    expect(fetcher).toHaveBeenLastCalledWith(0, undefined);
    expect(result.current.failed).toBe(false);
  });

  it('失敗しても適用済みの検索条件は保つ', async () => {
    // 起点へ戻すのは位置であって、利用者が適用した絞り込みではない
    let succeed = true;
    const fetcher = jest.fn(async (page: number) => {
      if (!succeed) throw new Error('boom');
      return pageOf(page);
    });
    const { result } = renderHook(() => useListPage<string, string>(fetcher, ''));
    await act(async () => {
      await result.current.search('やまだ');
    });

    succeed = false;
    await act(async () => {
      await result.current.reload();
    });
    succeed = true;
    await act(async () => {
      await result.current.reload();
    });

    expect(fetcher).toHaveBeenLastCalledWith(0, 'やまだ');
  });
});
