import { act, renderHook, waitFor } from '@testing-library/react';
import { useKeyedResource } from '../hooks/useKeyedResource';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

test('対象変更の最初の描画から旧対象を利用できず、保持値とは区別する', async () => {
  const next = deferred<string>();
  const seen: Array<string | null> = [];
  const { result, rerender } = renderHook(
    ({ id }) => {
      const resource = useKeyedResource(['order', id], () =>
        id === 'A' ? Promise.resolve('A') : next.promise
      );
      seen.push(resource.data);
      return resource;
    },
    { initialProps: { id: 'A' } }
  );
  await waitFor(() => expect(result.current.data).toBe('A'));
  seen.length = 0;
  rerender({ id: 'B' });
  expect(seen[0]).toBeNull();
  expect(result.current.retained?.data).toBe('A');
  await act(async () => next.resolve('B'));
  expect(result.current.data).toBe('B');
});

test.each(['success', 'error'] as const)('逆順の古い%sを結果として返さない', async status => {
  const first = deferred<string>();
  const second = deferred<string>();
  const fetcher = jest
    .fn()
    .mockResolvedValueOnce('initial')
    .mockReturnValueOnce(first.promise)
    .mockReturnValueOnce(second.promise);
  const { result } = renderHook(() => useKeyedResource(['order', 1], fetcher));
  await waitFor(() => expect(result.current.data).toBe('initial'));
  let old!: ReturnType<typeof result.current.reload>;
  let latest!: ReturnType<typeof result.current.reload>;
  act(() => {
    old = result.current.reload();
    latest = result.current.reload();
  });
  expect(result.current.data).toBe('initial');
  await act(async () => second.resolve('latest'));
  await act(async () => {
    if (status === 'success') first.resolve('old');
    else first.reject(new Error());
  });
  expect(await old).toEqual({ status: 'stale' });
  expect((await latest).status).toBe('success');
  expect(result.current.data).toBe('latest');
  expect(result.current.failure).toBeNull();
});

test('閉じて同じ対象を開き直すと保持値も旧操作の応答も利用しない', async () => {
  const old = deferred<string>();
  const fresh = deferred<string>();
  const fetcher = jest
    .fn()
    .mockResolvedValueOnce('initial')
    .mockReturnValueOnce(old.promise)
    .mockReturnValueOnce(fresh.promise);
  const { result, rerender } = renderHook(
    ({ open }) => useKeyedResource(['order', 1], open ? fetcher : null),
    { initialProps: { open: true } }
  );
  await waitFor(() => expect(result.current.data).toBe('initial'));
  const operation = result.current.capture();
  act(() => {
    void result.current.reload();
  });
  rerender({ open: false });
  rerender({ open: true });
  expect(result.current.data).toBeNull();
  expect(operation.replace('saved')).toBe(false);
  await act(async () => old.resolve('old'));
  expect(result.current.data).toBeNull();
  await act(async () => fresh.resolve('fresh'));
  expect(result.current.data).toBe('fresh');
});

test('直接反映は先行 GET を無効にして、新しい成功結果を公開する', async () => {
  const pending = deferred<string>();
  const { result } = renderHook(() => useKeyedResource(['order', 1], () => pending.promise));
  const operation = result.current.capture();
  act(() => {
    expect(operation.replace('saved')).toBe(true);
  });
  await act(async () => pending.resolve('old'));
  expect(result.current.data).toBe('saved');
});

test('同値キーで取得せず、条件変更では旧値を保持し、店舗変更では利用しない', async () => {
  const pending = deferred<string>();
  const fetcher = jest.fn().mockResolvedValueOnce('initial').mockReturnValue(pending.promise);
  const { result, rerender } = renderHook(
    ({ store, amount }) => useKeyedResource(['preview', store, 1], fetcher, [amount]),
    { initialProps: { store: 1, amount: 10 } }
  );
  await waitFor(() => expect(result.current.data).toBe('initial'));
  rerender({ store: 1, amount: 10 });
  expect(fetcher).toHaveBeenCalledTimes(1);
  rerender({ store: 1, amount: 20 });
  expect(result.current.data).toBe('initial');
  rerender({ store: 2, amount: 20 });
  expect(result.current.data).toBeNull();
  await act(async () => pending.resolve('new'));
});

test.each([undefined, { response: { status: 404 } }, new Error()])(
  '空 body と失敗を分類し再試行開始で畳む: %s',
  async error => {
    const fetcher = jest.fn().mockResolvedValueOnce('initial');
    if (error === undefined) fetcher.mockResolvedValueOnce(undefined);
    else fetcher.mockRejectedValueOnce(error);
    const retry = deferred<string>();
    fetcher.mockReturnValueOnce(retry.promise);
    const { result, unmount } = renderHook(() => useKeyedResource(['order', 1], fetcher));
    await waitFor(() => expect(result.current.data).toBe('initial'));
    await act(async () => {
      await result.current.reload();
    });
    expect(result.current.data).toBeNull();
    expect(result.current.failure).toBe(error && 'response' in error ? 'notFound' : 'error');
    let promise!: ReturnType<typeof result.current.reload>;
    act(() => {
      promise = result.current.reload();
    });
    expect(result.current.failure).toBeNull();
    const operation = result.current.capture();
    unmount();
    retry.resolve('late');
    expect(await promise).toEqual({ status: 'stale' });
    expect(operation.isCurrent()).toBe(false);
  }
);
