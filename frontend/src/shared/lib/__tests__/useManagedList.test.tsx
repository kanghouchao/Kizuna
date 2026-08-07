import { act, renderHook, waitFor } from '@testing-library/react';
import { useManagedList } from '@/shared/lib';

describe('useManagedList', () => {
  it('マウント時に取得し items と isLoading を管理する', async () => {
    const fetcher = jest.fn(async () => ['a', 'b']);
    const { result } = renderHook(() => useManagedList(fetcher));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.items).toEqual(['a', 'b']);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('refetch は再レンダー後の最新の fetcher を使う', async () => {
    const first = jest.fn(async () => ['first']);
    const second = jest.fn(async () => ['second']);
    const { result, rerender } = renderHook(({ fetcher }) => useManagedList(fetcher), {
      initialProps: { fetcher: first as () => Promise<string[]> },
    });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    rerender({ fetcher: second });
    await act(async () => {
      await result.current.refetch();
    });
    expect(second).toHaveBeenCalledTimes(1);
    expect(result.current.items).toEqual(['second']);
  });

  it('古いリクエストの遅延応答は新しい結果を上書きしない', async () => {
    const resolvers: Array<(value: string[]) => void> = [];
    const fetcher = jest.fn(() => new Promise<string[]>(resolve => resolvers.push(resolve)));
    const { result } = renderHook(() => useManagedList(fetcher));

    // マウント時の取得（1件目）が在途のまま 2 件目を発火し、2件目 → 1件目の順に解決する
    act(() => {
      void result.current.refetch();
    });
    await act(async () => {
      resolvers[1](['newer']);
    });
    await act(async () => {
      resolvers[0](['stale']);
    });

    expect(result.current.items).toEqual(['newer']);
    expect(result.current.isLoading).toBe(false);
  });

  it('古いリクエストの遅延失敗は新しい結果を消さない', async () => {
    // 失敗が items を空にするようになったため、守衛を落とすと在途の古い失敗が
    // 新しく届いた一覧を丸ごと消し、領域をエラー態へ倒してしまう
    let rejectFirst!: (reason?: unknown) => void;
    let resolveSecond!: (value: string[]) => void;
    const fetcher = jest
      .fn<Promise<string[]>, []>()
      .mockImplementationOnce(() => new Promise((_, reject) => (rejectFirst = reject)))
      .mockImplementationOnce(() => new Promise(resolve => (resolveSecond = resolve)));
    const { result } = renderHook(() => useManagedList(fetcher));

    act(() => {
      void result.current.refetch();
    });
    await act(async () => {
      resolveSecond(['newer']);
    });
    await act(async () => {
      rejectFirst(new Error('boom'));
    });

    expect(result.current.items).toEqual(['newer']);
    expect(result.current.failed).toBe(false);
  });

  it('失敗時は failed を立てて表示中の項目を消し、loading を解除する', async () => {
    // 古い項目を残すと「読めなかった」が「これが最新」に化ける
    let succeed = true;
    const { result } = renderHook(() =>
      useManagedList(async () => {
        if (!succeed) throw new Error('boom');
        return ['a'];
      })
    );
    await waitFor(() => expect(result.current.items).toEqual(['a']));

    succeed = false;
    await act(async () => {
      await result.current.refetch();
    });

    expect(result.current.failed).toBe(true);
    expect(result.current.items).toEqual([]);
    expect(result.current.isLoading).toBe(false);
  });

  it('再取得を始めた時点で失敗表示を畳む', async () => {
    let succeed = false;
    const { result } = renderHook(() =>
      useManagedList(async () => {
        if (!succeed) throw new Error('boom');
        return ['a'];
      })
    );
    await waitFor(() => expect(result.current.failed).toBe(true));

    succeed = true;
    await act(async () => {
      await result.current.refetch();
    });

    expect(result.current.failed).toBe(false);
    expect(result.current.items).toEqual(['a']);
  });
});
