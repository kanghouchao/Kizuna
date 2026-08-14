import { act, renderHook, waitFor } from '@testing-library/react';
import { CursorPageResult } from '@/shared/api';
import { useCursorList } from '@/shared/lib';

/** カーソルで続きを返すサーバの代役。位置は「次に返す行の番号」で表す。 */
const cursorServer =
  (total: number, size: number) =>
  async (cursor?: string): Promise<CursorPageResult<string>> => {
    const start = cursor ? Number(cursor) : 0;
    const end = Math.min(start + size, total);
    return {
      rows: Array.from({ length: end - start }, (_, i) => `row${start + i}`),
      nextCursor: end < total ? String(end) : null,
    };
  };

describe('useCursorList', () => {
  it('マウント時に先頭を取り、続きの有無を伝える', async () => {
    const fetcher = jest.fn(cursorServer(5, 2));
    const { result } = renderHook(() => useCursorList(fetcher));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.rows).toEqual(['row0', 'row1']);
    expect(result.current.hasMore).toBe(true);
    expect(fetcher).toHaveBeenCalledWith(undefined, undefined);
  });

  it('追加読み込みは続きの位置から 1 回だけ取り、表示中の行に継ぎ足す', async () => {
    const fetcher = jest.fn(cursorServer(5, 2));
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.hasMore).toBe(true));

    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.rows).toEqual(['row0', 'row1', 'row2', 'row3']));
    // 読み込み済みの範囲は読み直さない（読み直す実装では、ここまでで 3 要求が飛ぶ）
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher).toHaveBeenNthCalledWith(2, '2', undefined);
  });

  it('処理で表示中の行を取り除いても、続きは同じ位置から続く', async () => {
    // 位置を「何件目か」で持つと、行が消えた分だけ後続が繰り上がり境界の行を飛ばす
    const fetcher = jest.fn(cursorServer(5, 2));
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.hasMore).toBe(true));

    act(() => result.current.setRows(prev => prev.filter(row => row !== 'row0')));
    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.rows).toEqual(['row1', 'row2', 'row3']));
    expect(fetcher).toHaveBeenNthCalledWith(2, '2', undefined);
  });

  it('古いリクエストの遅延応答は新しい結果を上書きしない', async () => {
    // 在途の追加読み込みが後から届くと、取り直した先頭に続きが二重で継ぎ足される
    let resolveStale!: (value: CursorPageResult<string>) => void;
    let resolveLatest!: (value: CursorPageResult<string>) => void;
    const fetcher = jest
      .fn<Promise<CursorPageResult<string>>, [string?]>()
      .mockResolvedValueOnce({ rows: ['row0', 'row1'], nextCursor: '2' })
      .mockImplementationOnce(() => new Promise(resolve => (resolveStale = resolve)))
      .mockImplementationOnce(() => new Promise(resolve => (resolveLatest = resolve)));
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.hasMore).toBe(true));

    // 追加読み込みを在途のまま先頭から取り直し、取り直し → 追加読み込みの順に解決する
    act(() => result.current.loadMore());
    act(() => result.current.reload());
    await act(async () => {
      resolveLatest({ rows: ['row0', 'row1'], nextCursor: '2' });
    });
    await act(async () => {
      resolveStale({ rows: ['row2', 'row3'], nextCursor: '4' });
    });

    expect(result.current.rows).toEqual(['row0', 'row1']);
    expect(fetcher).toHaveBeenCalledTimes(3);
  });

  it('古いリクエストの遅延失敗は新しい結果を消さない', async () => {
    // 失敗が行と位置の両方を起点へ戻すため、守衛を落とすと在途の古い失敗が新しく
    // 届いた一覧を丸ごと消し、「もっと見る」ごと領域をエラー態へ倒してしまう
    let rejectStale!: (reason?: unknown) => void;
    let resolveLatest!: (value: CursorPageResult<string>) => void;
    const fetcher = jest
      .fn<Promise<CursorPageResult<string>>, [string?]>()
      .mockImplementationOnce(() => new Promise((_, reject) => (rejectStale = reject)))
      .mockImplementationOnce(() => new Promise(resolve => (resolveLatest = resolve)));
    const { result } = renderHook(() => useCursorList(fetcher));

    // マウント時の取得を在途のまま取り直し、取り直し → マウント時の順に解決する
    act(() => result.current.reload());
    await act(async () => {
      resolveLatest({ rows: ['row0', 'row1'], nextCursor: '2' });
    });
    await act(async () => {
      rejectStale(new Error('network'));
    });

    expect(result.current.rows).toEqual(['row0', 'row1']);
    expect(result.current.failed).toBe(false);
    expect(result.current.hasMore).toBe(true);
  });

  it('先頭の取得に失敗したら、空表示と区別できる失敗として伝える', async () => {
    const fetcher = jest.fn(async () => {
      throw new Error('network');
    });
    const { result } = renderHook(() => useCursorList(fetcher));

    await waitFor(() => expect(result.current.failed).toBe(true));
    expect(result.current.rows).toEqual([]);
  });

  it('追加読み込みの失敗も領域ごと失敗させ、表示中の行を消す', async () => {
    // 表示中の行を残すと、読めなかった領域に前回の内容が居座り「これが最新」に見える
    const server = cursorServer(5, 2);
    const fetcher = jest.fn((cursor?: string) =>
      cursor ? Promise.reject(new Error('network')) : server(cursor)
    );
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.hasMore).toBe(true));

    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.failed).toBe(true));
    expect(result.current.rows).toEqual([]);
    expect(result.current.hasMore).toBe(false);
  });

  it('追加読み込みに失敗した後の再取得は先頭から取り直す', async () => {
    // 失敗した位置から再開すると、1〜20 行目を欠いたまま 21 行目以降だけが返る
    const server = cursorServer(5, 2);
    const fetcher = jest.fn((cursor?: string) =>
      cursor ? Promise.reject(new Error('network')) : server(cursor)
    );
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.hasMore).toBe(true));

    act(() => result.current.loadMore());
    await waitFor(() => expect(result.current.failed).toBe(true));

    fetcher.mockImplementation(server);
    act(() => result.current.reload());

    await waitFor(() => expect(result.current.rows).toEqual(['row0', 'row1']));
    expect(fetcher).toHaveBeenLastCalledWith(undefined, undefined);
    expect(result.current.failed).toBe(false);
  });

  it('reload は先頭から取り直し、失敗表示を畳む', async () => {
    const server = cursorServer(5, 2);
    let recovered = false;
    const fetcher = jest.fn((cursor?: string) =>
      recovered ? server(cursor) : Promise.reject(new Error('network'))
    );
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.failed).toBe(true));

    recovered = true;
    act(() => result.current.reload());

    await waitFor(() => expect(result.current.rows).toEqual(['row0', 'row1']));
    expect(result.current.failed).toBe(false);
  });

  it('検索条件を適用すると先頭から取り直し、続きの取得もその条件で行う', async () => {
    // 条件が変わっても取り直さない形だと、並び替えや検索を押しても一覧が動かない退行が静かに通る
    const fetcher = jest.fn(async (cursor: string | undefined, criteria: { q: string }) => ({
      rows: [{ id: `${criteria.q}-${cursor ?? 'head'}` }],
      nextCursor: 'c1',
    }));
    const { result } = renderHook(() => useCursorList(fetcher, { q: 'a' }));

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(fetcher).toHaveBeenLastCalledWith(undefined, { q: 'a' });

    act(() => result.current.search({ q: 'b' }));

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    // 位置も起点へ戻す — 前の条件で得たカーソルは新しい並びの中では別の場所を指す
    expect(fetcher).toHaveBeenLastCalledWith(undefined, { q: 'b' });
    expect(result.current.rows).toEqual([{ id: 'b-head' }]);

    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(fetcher).toHaveBeenLastCalledWith('c1', { q: 'b' });
  });
});
