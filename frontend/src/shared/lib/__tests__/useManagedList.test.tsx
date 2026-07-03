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

  it('refetch は最新のクロージャで再取得する', async () => {
    let value = ['first'];
    const { result } = renderHook(() => useManagedList(async () => value, '取得失敗'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    value = ['second'];
    await act(async () => {
      await result.current.refetch();
    });
    expect(result.current.items).toEqual(['second']);
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
