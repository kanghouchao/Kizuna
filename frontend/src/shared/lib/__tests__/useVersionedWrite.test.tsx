import { act, renderHook, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { useVersionedWrite } from '@/shared/lib';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const messages = { success: '保存しました', failure: '保存に失敗しました' };
const rejectWith = (status: number, error?: string) => () =>
  Promise.reject({ response: { status, data: error ? { error } : {} } });

const setup = () => {
  const reloadTarget = jest.fn(async () => {});
  const rendered = renderHook(() => useVersionedWrite(reloadTarget));
  return { ...rendered, reloadTarget };
};

describe('useVersionedWrite', () => {
  beforeEach(() => jest.clearAllMocks());

  it('成功は応答を反映して成功文言を出し、取り直さない', async () => {
    const { result, reloadTarget } = setup();
    const onSuccess = jest.fn();

    let outcome;
    await act(async () => {
      outcome = await result.current.write(async () => ({ version: 2 }), {
        ...messages,
        onSuccess,
      });
    });

    expect(outcome).toBe('success');
    expect(onSuccess).toHaveBeenCalledWith({ version: 2 });
    expect(notify.success).toHaveBeenCalledWith('保存しました');
    expect(reloadTarget).not.toHaveBeenCalled();
    expect(result.current.busy).toBe(false);
  });

  it('409 は競合を立てて取り直すまで次を送らず、reload が競合を畳む', async () => {
    const { result, reloadTarget } = setup();

    let outcome;
    await act(async () => {
      outcome = await result.current.write(rejectWith(409, '更新されています'), messages);
    });
    expect(outcome).toBe('conflict');
    expect(result.current.conflict).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('更新されています');
    expect(reloadTarget).not.toHaveBeenCalled();

    const send = jest.fn(async () => null);
    await act(async () => {
      outcome = await result.current.write(send, messages);
    });
    expect(outcome).toBe('refused');
    expect(send).not.toHaveBeenCalled();

    await act(async () => {
      await result.current.reload();
    });
    expect(reloadTarget).toHaveBeenCalledTimes(1);
    expect(result.current.conflict).toBe(false);
    await act(async () => {
      outcome = await result.current.write(send, messages);
    });
    expect(outcome).toBe('success');
  });

  it('404 は失敗文言を出して対象を取り直し、競合は立てない', async () => {
    const { result, reloadTarget } = setup();

    let outcome;
    await act(async () => {
      outcome = await result.current.write(rejectWith(404), messages);
    });

    expect(outcome).toBe('notFound');
    expect(notify.error).toHaveBeenCalledWith('保存に失敗しました');
    expect(reloadTarget).toHaveBeenCalledTimes(1);
    expect(result.current.conflict).toBe(false);
  });

  it('その他の失敗は文言だけを出し、取り直さない', async () => {
    const { result, reloadTarget } = setup();

    let outcome;
    await act(async () => {
      outcome = await result.current.write(rejectWith(400, '入力が不正です'), messages);
    });

    expect(outcome).toBe('failed');
    expect(notify.error).toHaveBeenCalledWith('入力が不正です');
    expect(reloadTarget).not.toHaveBeenCalled();
    expect(result.current.conflict).toBe(false);
  });

  it('取り直しが終わるまでは、409 の後も 404 の後も次を送らない', async () => {
    let finishReload: () => void = () => {};
    const reloadTarget = jest.fn(() => new Promise<void>(resolve => (finishReload = resolve)));
    const { result } = renderHook(() => useVersionedWrite(reloadTarget));
    const send = jest.fn(async () => null);

    await act(async () => {
      await result.current.write(rejectWith(409), messages);
    });
    let reloading: Promise<void> = Promise.resolve();
    act(() => {
      reloading = result.current.reload();
    });
    expect(await act(() => result.current.write(send, messages))).toBe('refused');
    await act(async () => {
      finishReload();
      await reloading;
    });
    expect(result.current.conflict).toBe(false);

    let notFound: Promise<unknown> = Promise.resolve();
    act(() => {
      notFound = result.current.write(rejectWith(404), messages);
    });
    await waitFor(() => expect(reloadTarget).toHaveBeenCalledTimes(2));
    expect(await act(() => result.current.write(send, messages))).toBe('refused');
    expect(result.current.busy).toBe(true);
    await act(async () => {
      finishReload();
      await notFound;
    });
    expect(result.current.busy).toBe(false);
    expect(send).not.toHaveBeenCalled();
  });

  it('送信中の二度目は同じ描画の中でも送らない', async () => {
    const { result } = setup();
    let finish: (value: null) => void = () => {};
    const first = jest.fn(() => new Promise<null>(resolve => (finish = resolve)));
    const second = jest.fn(async () => null);

    let pending: Promise<unknown> = Promise.resolve();
    let outcome;
    await act(async () => {
      pending = result.current.write(first, messages);
      outcome = await result.current.write(second, messages);
    });
    expect(outcome).toBe('refused');
    expect(second).not.toHaveBeenCalled();
    expect(result.current.busy).toBe(true);

    await act(async () => {
      finish(null);
      await pending;
    });
    expect(result.current.busy).toBe(false);
  });
});
