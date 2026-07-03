import { act, renderHook, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { useManagedList } from '@/shared/lib';

jest.mock('react-hot-toast', () => ({
  toast: { error: jest.fn() },
}));

describe('useManagedList', () => {
  it('マウント時に取得し items と isLoading を管理する', async () => {
    const fetcher = jest.fn(async () => ['a', 'b']);
    const { result } = renderHook(() => useManagedList(fetcher, '取得失敗'));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.items).toEqual(['a', 'b']);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('refetch は再レンダー後の最新の fetcher を使う', async () => {
    const first = jest.fn(async () => ['first']);
    const second = jest.fn(async () => ['second']);
    const { result, rerender } = renderHook(({ fetcher }) => useManagedList(fetcher, '取得失敗'), {
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
    const { result } = renderHook(() => useManagedList(fetcher, '取得失敗'));

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

  it('アンマウント後に失敗したリクエストはトーストを出さない', async () => {
    let rejectRequest!: (reason?: unknown) => void;
    const fetcher = () =>
      new Promise<string[]>((_, reject) => {
        rejectRequest = reject;
      });
    const { unmount } = renderHook(() => useManagedList(fetcher, '取得失敗'));

    unmount();
    await act(async () => {
      rejectRequest(new Error('boom'));
    });

    expect(toast.error).not.toHaveBeenCalled();
  });

  it('失敗時はトーストを出し loading を解除する', async () => {
    const { result } = renderHook(() =>
      useManagedList(async () => {
        throw new Error('boom');
      }, '取得失敗')
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(toast.error).toHaveBeenCalledWith('取得失敗');
    expect(result.current.items).toEqual([]);
  });
});
