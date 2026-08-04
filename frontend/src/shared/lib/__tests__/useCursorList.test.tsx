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
    expect(fetcher).toHaveBeenCalledWith(undefined);
  });

  it('追加読み込みは続きの位置から 1 回だけ取り、表示中の行に継ぎ足す', async () => {
    const fetcher = jest.fn(cursorServer(5, 2));
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.hasMore).toBe(true));

    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.rows).toEqual(['row0', 'row1', 'row2', 'row3']));
    // 読み込み済みの範囲は読み直さない（読み直す実装では、ここまでで 3 要求が飛ぶ）
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher).toHaveBeenNthCalledWith(2, '2');
  });

  it('処理で表示中の行を取り除いても、続きは同じ位置から続く', async () => {
    // 位置を「何件目か」で持つと、行が消えた分だけ後続が繰り上がり境界の行を飛ばす
    const fetcher = jest.fn(cursorServer(5, 2));
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.hasMore).toBe(true));

    act(() => result.current.setRows(prev => prev.filter(row => row !== 'row0')));
    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.rows).toEqual(['row1', 'row2', 'row3']));
    expect(fetcher).toHaveBeenNthCalledWith(2, '2');
  });

  it('先頭の取得に失敗したら、空表示と区別できる失敗として伝える', async () => {
    const fetcher = jest.fn(async () => {
      throw new Error('network');
    });
    const { result } = renderHook(() => useCursorList(fetcher));

    await waitFor(() => expect(result.current.failed).toBe(true));
    expect(result.current.loadMoreFailed).toBe(false);
  });

  it('追加読み込みに失敗しても表示中の行を消さず、同じ位置から再試行できる', async () => {
    const server = cursorServer(5, 2);
    const fetcher = jest.fn((cursor?: string) =>
      cursor ? Promise.reject(new Error('network')) : server(cursor)
    );
    const { result } = renderHook(() => useCursorList(fetcher));
    await waitFor(() => expect(result.current.hasMore).toBe(true));

    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.loadMoreFailed).toBe(true));
    expect(result.current.failed).toBe(false);
    expect(result.current.rows).toEqual(['row0', 'row1']);

    fetcher.mockImplementation(server);
    act(() => result.current.loadMore());

    // 続きの位置を進めていないので、失敗した続きと同じ位置から取り直す
    await waitFor(() => expect(result.current.rows).toHaveLength(4));
    expect(fetcher).toHaveBeenLastCalledWith('2');
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
});
