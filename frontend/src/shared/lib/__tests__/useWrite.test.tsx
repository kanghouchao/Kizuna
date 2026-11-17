import { startTransition, useState } from 'react';
import { act, renderHook } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { useWrite } from '@/shared/lib';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const failure = '保存に失敗しました';
const rejectWith = (status: number, error?: string) => () =>
  Promise.reject({ response: { status, data: error ? { error } : {} } });

function deferred<T = void>() {
  let resolve: (value: T) => void = () => {};
  let reject: (reason: unknown) => void = () => {};
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('useWrite', () => {
  beforeEach(() => jest.clearAllMocks());

  it('成功は成功文言を出して応答を反映し、busy を戻す', async () => {
    const { result } = renderHook(() => useWrite());
    const onSuccess = jest.fn();

    let outcome;
    await act(async () => {
      outcome = await result.current.write(async () => ({ id: 1 }), {
        success: '保存しました',
        failure,
        onSuccess,
      });
    });

    expect(outcome).toBe('success');
    expect(notify.success).toHaveBeenCalledWith('保存しました');
    expect(onSuccess).toHaveBeenCalledWith({ id: 1 });
    expect(notify.error).not.toHaveBeenCalled();
    expect(result.current.busy).toBe(false);
  });

  it('success を省くと成功の案内を出さない', async () => {
    const { result } = renderHook(() => useWrite());
    const onSuccess = jest.fn();

    await act(async () => {
      await result.current.write(async () => null, { failure, onSuccess });
    });

    expect(onSuccess).toHaveBeenCalled();
    expect(notify.success).not.toHaveBeenCalled();
  });

  it.each([
    ['サーバの理由', rejectWith(400, '入力が不正です'), '入力が不正です'],
    ['既定の文言', rejectWith(500), failure],
  ])('failure が文言の失敗は%sを error で出し、反映しない', async (_, send, message) => {
    const { result } = renderHook(() => useWrite());
    const onSuccess = jest.fn();

    let outcome;
    await act(async () => {
      outcome = await result.current.write(send, { success: '保存しました', failure, onSuccess });
    });

    expect(outcome).toBe('failed');
    expect(notify.error).toHaveBeenCalledWith(message);
    expect(notify.success).not.toHaveBeenCalled();
    expect(onSuccess).not.toHaveBeenCalled();
    expect(result.current.busy).toBe(false);
  });

  it('failure が関数ならエラーを渡して案内を任せる', async () => {
    const { result } = renderHook(() => useWrite());
    const error = { response: { status: 409, data: {} } };
    const present = jest.fn();

    let outcome;
    await act(async () => {
      outcome = await result.current.write(() => Promise.reject(error), { failure: present });
    });

    expect(outcome).toBe('failed');
    expect(present).toHaveBeenCalledWith(error);
    expect(notify.error).not.toHaveBeenCalled();
  });

  it('onFailure にエラーを渡し、返した promise が終わるまで busy を残す', async () => {
    const { result } = renderHook(() => useWrite());
    const error = { response: { status: 404, data: {} } };
    const reloading = deferred();
    const onFailure = jest.fn(() => reloading.promise);

    let pending: Promise<unknown> = Promise.resolve();
    await act(async () => {
      pending = result.current.write(() => Promise.reject(error), { failure, onFailure });
    });

    expect(onFailure).toHaveBeenCalledWith(error);
    expect(notify.error).toHaveBeenCalledWith(failure);
    expect(result.current.busy).toBe(true);

    await act(async () => {
      reloading.resolve();
      await pending;
    });
    expect(await pending).toBe('failed');
    expect(result.current.busy).toBe(false);
  });

  it('同じフレームの二度目の呼び出しは送らない', async () => {
    const { result } = renderHook(() => useWrite());
    const response = deferred<null>();
    const send = jest.fn(() => response.promise);

    let pending: Promise<unknown> = Promise.resolve();
    let second;
    await act(async () => {
      pending = result.current.write(send, { failure });
      second = await result.current.write(send, { failure });
    });

    expect(second).toBe('refused');
    expect(send).toHaveBeenCalledTimes(1);
    expect(result.current.busy).toBe(true);

    await act(async () => {
      response.resolve(null);
      await pending;
    });
    expect(result.current.busy).toBe(false);
  });

  it('onSuccess が返した promise が終わるまで busy を残す', async () => {
    const { result } = renderHook(() => useWrite());
    const reloading = deferred();

    let pending: Promise<unknown> = Promise.resolve();
    await act(async () => {
      pending = result.current.write(async () => null, {
        failure,
        onSuccess: () => reloading.promise,
      });
    });
    expect(result.current.busy).toBe(true);

    await act(async () => {
      reloading.resolve();
      await pending;
    });
    expect(result.current.busy).toBe(false);
  });

  it('onSuccess が始めた遷移が反映されるまで busy を残す', async () => {
    const { result } = renderHook(() => useWrite());
    const navigating = deferred();
    // router.push と同じく何も返さず、自分の transition の中で画面を進める
    const push = () => {
      startTransition(async () => {
        await navigating.promise;
      });
    };

    await act(async () => {
      await result.current.write(async () => null, { failure, onSuccess: push });
    });
    expect(result.current.busy).toBe(true);

    await act(async () => {
      navigating.resolve();
    });
    expect(result.current.busy).toBe(false);
  });

  it('onSuccess が同期に出した更新は、返した promise が終わるまで描かれない', async () => {
    const reloading = deferred();
    const { result } = renderHook(() => {
      const [closed, setClosed] = useState(false);
      return { closed, setClosed, ...useWrite() };
    });

    let pending: Promise<unknown> = Promise.resolve();
    await act(async () => {
      pending = result.current.write(async () => null, {
        failure,
        onSuccess: () => {
          result.current.setClosed(true);
          return reloading.promise;
        },
      });
    });
    expect(result.current.closed).toBe(false);

    await act(async () => {
      reloading.resolve();
      await pending;
    });
    expect(result.current.closed).toBe(true);
  });

  it('onSuccess の例外は失敗として案内せず、呼び出し元へ返して busy を戻す', async () => {
    const { result } = renderHook(() => useWrite());
    const broken = new Error('反映に失敗');

    let thrown;
    await act(async () => {
      thrown = await result.current
        .write(async () => null, {
          success: '保存しました',
          failure,
          onSuccess: () => {
            throw broken;
          },
        })
        .catch(error => error);
    });

    expect(thrown).toBe(broken);
    expect(notify.success).toHaveBeenCalledWith('保存しました');
    expect(notify.error).not.toHaveBeenCalled();
    expect(result.current.busy).toBe(false);

    const send = jest.fn(async () => null);
    await act(async () => {
      await result.current.write(send, { failure });
    });
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('画面が外れた後の応答は、案内だけを出して反映しない', async () => {
    const { result, unmount } = renderHook(() => useWrite());
    const response = deferred<null>();
    const onSuccess = jest.fn();

    let pending: Promise<unknown> = Promise.resolve();
    act(() => {
      pending = result.current.write(() => response.promise, {
        success: '保存しました',
        failure,
        onSuccess,
      });
    });
    unmount();
    await act(async () => {
      response.resolve(null);
      await pending;
    });

    expect(notify.success).toHaveBeenCalledWith('保存しました');
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('画面が外れた後の失敗は、案内だけを出して反映しない', async () => {
    const { result, unmount } = renderHook(() => useWrite());
    const response = deferred<null>();
    const onFailure = jest.fn();

    let pending: Promise<unknown> = Promise.resolve();
    act(() => {
      pending = result.current.write(() => response.promise, { failure, onFailure });
    });
    unmount();
    await act(async () => {
      response.reject({ response: { status: 500, data: {} } });
      await pending;
    });

    expect(notify.error).toHaveBeenCalledWith(failure);
    expect(onFailure).not.toHaveBeenCalled();
  });
});
