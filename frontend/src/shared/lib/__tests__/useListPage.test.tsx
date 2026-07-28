import { act, renderHook, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { useListPage } from '@/shared/lib';
import { PageResult } from '@/shared/api';

jest.mock('react-hot-toast', () => ({
  toast: { error: jest.fn() },
}));

const pageOf = (page: number, rows: string[] = ['a']): PageResult<string> => ({
  rows,
  page,
  pageCount: 3,
  total: 21,
});

describe('useListPage', () => {
  it('マウント時に 0 ページ目を取得し pageResult と isLoading を管理する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page, ['a', 'b']));
    const { result } = renderHook(() => useListPage(fetcher, '取得失敗'));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.rows).toEqual(['a', 'b']);
    expect(result.current.page).toBe(0);
    expect(fetcher).toHaveBeenCalledWith(0);
  });

  it('onPageChange は指定ページで再取得する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() => useListPage(fetcher, '取得失敗'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.onPageChange(2);
    });

    expect(fetcher).toHaveBeenLastCalledWith(2);
    expect(result.current.page).toBe(2);
  });

  it('reload は現在のページのまま再取得する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() => useListPage(fetcher, '取得失敗'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.onPageChange(2);
    });

    await act(async () => {
      await result.current.reload();
    });

    expect(fetcher).toHaveBeenLastCalledWith(2);
    expect(result.current.page).toBe(2);
  });

  it('search は現在ページに関わらず 0 ページ目へ戻して再取得する', async () => {
    const fetcher = jest.fn(async (page: number) => pageOf(page));
    const { result } = renderHook(() => useListPage(fetcher, '取得失敗'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.onPageChange(2);
    });
    expect(result.current.page).toBe(2);

    await act(async () => {
      await result.current.search();
    });

    expect(fetcher).toHaveBeenLastCalledWith(0);
    expect(result.current.page).toBe(0);
  });

  it('古いリクエストの遅延応答は新しい結果を上書きしない', async () => {
    const resolvers: Array<(value: PageResult<string>) => void> = [];
    const fetcher = jest.fn(
      () => new Promise<PageResult<string>>(resolve => resolvers.push(resolve))
    );
    const { result } = renderHook(() => useListPage(fetcher, '取得失敗'));

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

  it('アンマウント後に失敗したリクエストはトーストを出さない', async () => {
    let rejectRequest!: (reason?: unknown) => void;
    const fetcher = () =>
      new Promise<PageResult<string>>((_, reject) => {
        rejectRequest = reject;
      });
    const { unmount } = renderHook(() => useListPage(fetcher, '取得失敗'));

    unmount();
    await act(async () => {
      rejectRequest(new Error('boom'));
    });

    expect(toast.error).not.toHaveBeenCalled();
  });

  it('失敗時はトーストを出し loading を解除する', async () => {
    const { result } = renderHook(() =>
      useListPage(async () => {
        throw new Error('boom');
      }, '取得失敗')
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(toast.error).toHaveBeenCalledWith('取得失敗');
    expect(result.current.rows).toEqual([]);
  });
});
