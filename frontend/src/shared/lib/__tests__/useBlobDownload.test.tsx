import { act, renderHook } from '@testing-library/react';
import { useBlobDownload } from '@/shared/lib';

function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
const blob = () => new Blob(['a'], { type: 'text/csv' });
let click: jest.SpyInstance;
let count = 0;
beforeEach(() => {
  count = 0;
  URL.createObjectURL = jest.fn(() => `blob:${++count}`);
  URL.revokeObjectURL = jest.fn();
  click = jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement
  ) {
    expect(this.isConnected).toBe(true);
  });
});
afterEach(() => jest.restoreAllMocks());

it('取得中は busy で、取得後にファイル名付きで保存し URL は次の取得まで解放しない', async () => {
  const { result } = renderHook(() => useBlobDownload());
  const first = deferred<Blob>();
  let saved: Promise<string | null>;
  act(() => {
    saved = result.current.run(() => first.promise, 'a.csv');
  });
  expect(result.current.busy).toBe(true);
  await act(async () => first.resolve(blob()));
  expect(await saved!).toBe('blob:1');
  expect(click).toHaveBeenCalledTimes(1);
  expect(click.mock.contexts[0]).toMatchObject({ download: 'a.csv', href: 'blob:1' });
  expect(result.current).toMatchObject({ busy: false, error: null, url: 'blob:1' });
  expect(URL.revokeObjectURL).not.toHaveBeenCalled();

  await act(async () => void (await result.current.run(async () => blob())));
  expect(click).toHaveBeenCalledTimes(1);
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1');
  expect(result.current.url).toBe('blob:2');
});

it('unmount で取得を中止して URL を解放し、遅れた応答では何も作らない', async () => {
  const { result, unmount } = renderHook(() => useBlobDownload());
  await act(async () => void (await result.current.run(async () => blob())));
  const late = deferred<Blob>();
  let signal!: AbortSignal;
  act(() => {
    void result.current.run(s => {
      signal = s;
      return late.promise;
    });
  });
  unmount();
  expect(signal.aborted).toBe(true);
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1');
  await act(async () => late.resolve(blob()));
  expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
});

it('次の取得は進行中の取得を中止し、古い応答の失敗を表示しない', async () => {
  const { result } = renderHook(() => useBlobDownload());
  const old = deferred<Blob>();
  let oldSignal!: AbortSignal;
  act(() => {
    void result.current.run(s => {
      oldSignal = s;
      return old.promise;
    });
  });
  const current = deferred<Blob>();
  act(() => {
    void result.current.run(() => current.promise);
  });
  expect(oldSignal.aborted).toBe(true);
  await act(async () => old.reject(new Error('古い失敗')));
  expect(result.current).toMatchObject({ busy: true, error: null });
  await act(async () => current.resolve(blob()));
  expect(result.current).toMatchObject({ busy: false, url: 'blob:1' });
});

it('失敗は文言を残して URL を捨て、再試行は同じ取得とファイル名を繰り返す', async () => {
  const { result } = renderHook(() => useBlobDownload());
  await act(async () => void (await result.current.run(async () => blob())));
  const fetcher = jest.fn().mockRejectedValueOnce(new Error('混み合っています'));
  fetcher.mockResolvedValueOnce(blob());
  await act(async () => void (await result.current.run(fetcher, 'r.csv')));
  expect(result.current).toMatchObject({ busy: false, error: '混み合っています', url: null });
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1');
  await act(async () => void (await result.current.retry()));
  expect(fetcher).toHaveBeenCalledTimes(2);
  expect(result.current).toMatchObject({ error: null, url: 'blob:2' });
  expect(click.mock.contexts.at(-1)).toMatchObject({ download: 'r.csv' });
});

it('取消は取得を中止して busy を下ろし、失敗も保存も残さない', async () => {
  const { result } = renderHook(() => useBlobDownload());
  const pending = deferred<Blob>();
  let signal!: AbortSignal;
  act(() => {
    void result.current.run(s => {
      signal = s;
      return pending.promise;
    }, 'c.csv');
  });
  act(() => result.current.cancel());
  expect(signal.aborted).toBe(true);
  expect(result.current.busy).toBe(false);
  await act(async () => pending.reject(new Error('canceled')));
  expect(result.current).toMatchObject({ busy: false, error: null, url: null });
  expect(click).not.toHaveBeenCalled();
});
