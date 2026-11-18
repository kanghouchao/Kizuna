import { startTransition } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { useResource, useVersionedWrite } from '@/shared/lib';

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

  it('競合は busy が戻るのと同じ描画で立てる', async () => {
    const reloadTarget = jest.fn(async () => {});
    const renders: { busy: boolean; conflict: boolean }[] = [];
    const { result } = renderHook(() => {
      const writer = useVersionedWrite(reloadTarget);
      renders.push({ busy: writer.busy, conflict: writer.conflict });
      return writer;
    });

    await act(async () => {
      await result.current.write(rejectWith(409), messages);
    });

    expect(result.current.conflict).toBe(true);
    // 送信中のまま競合の案内を出すと、案内の「読み込む」を押しても画面が送信中として捨てる
    expect(renders).not.toContainEqual({ busy: true, conflict: true });
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

  it.each([
    [409, 'conflict', true],
    [404, 'notFound', false],
    [403, 'forbidden', false],
    [400, 'failed', false],
  ])(
    'failure に関数を渡すと %i のエラーと種類 %s を受け取り、案内は出さない',
    async (status, kind, conflict) => {
      const { result, reloadTarget } = setup();
      const failure = jest.fn();
      const onFailure = jest.fn();
      const error = { response: { status, data: { error: 'サーバの文言' } } };

      let outcome;
      await act(async () => {
        outcome = await result.current.write(() => Promise.reject(error), { failure, onFailure });
      });

      expect(outcome).toBe(kind);
      expect(failure).toHaveBeenCalledWith(error, kind);
      expect(onFailure).toHaveBeenCalledWith(error, kind);
      expect(notify.error).not.toHaveBeenCalled();
      expect(notify.success).not.toHaveBeenCalled();
      expect(result.current.conflict).toBe(conflict);
      expect(reloadTarget).toHaveBeenCalledTimes(status === 404 ? 1 : 0);
    }
  );

  it('403 は文言の案内を出し、forbidden を返して取り直さない', async () => {
    const { result, reloadTarget } = setup();

    let outcome;
    await act(async () => {
      outcome = await result.current.write(rejectWith(403), messages);
    });

    expect(outcome).toBe('forbidden');
    expect(notify.error).toHaveBeenCalledWith('保存に失敗しました');
    expect(reloadTarget).not.toHaveBeenCalled();
    expect(result.current.conflict).toBe(false);
  });

  it('onFailure は 404 の取り直しが終わってから呼び、返した promise の完了まで busy を残す', async () => {
    const order: string[] = [];
    const reloadTarget = jest.fn(async () => {
      order.push('reload');
    });
    const { result } = renderHook(() => useVersionedWrite(reloadTarget));
    let finish: () => void = () => {};
    const onFailure = jest.fn(() => {
      order.push('onFailure');
      return new Promise<void>(resolve => (finish = resolve));
    });

    let pending: Promise<unknown> = Promise.resolve();
    act(() => {
      pending = result.current.write(rejectWith(404), { ...messages, onFailure });
    });
    await waitFor(() => expect(onFailure).toHaveBeenCalled());
    expect(order).toEqual(['reload', 'onFailure']);
    expect(result.current.busy).toBe(true);

    await act(async () => {
      finish();
      await pending;
    });
    expect(result.current.busy).toBe(false);
  });

  it('reload は取り直しの結果を返す', async () => {
    const { result } = renderHook(() => useVersionedWrite(async () => 'latest'));

    let reloaded;
    await act(async () => {
      reloaded = await result.current.reload();
    });

    expect(reloaded).toBe('latest');
  });

  it('409 の結末を受けてから始めた取り直しは、取得中の表示を描く', async () => {
    let finishFetch: (value: string) => void = () => {};
    const fetcher = jest
      .fn<Promise<string>, []>()
      .mockResolvedValueOnce('古い内容')
      .mockImplementationOnce(() => new Promise(resolve => (finishFetch = resolve)));
    const { result } = renderHook(() => {
      const resource = useResource(fetcher);
      return { resource, writer: useVersionedWrite(resource.reload) };
    });
    await waitFor(() => expect(result.current.resource.data).toBe('古い内容'));

    let reloading: Promise<void> = Promise.resolve();
    await act(async () => {
      const outcome = await result.current.writer.write(rejectWith(409), messages);
      if (outcome === 'conflict') reloading = result.current.writer.reload();
    });
    expect(result.current.resource.isLoading).toBe(true);
    // 取得中は busy が戻っていても、競合の標識が次の書き込みを止める
    expect(result.current.writer.busy).toBe(false);
    const send = jest.fn(async () => null);
    expect(await act(() => result.current.writer.write(send, messages))).toBe('refused');

    await act(async () => {
      finishFetch('最新の内容');
      await reloading;
    });
    expect(result.current.resource.isLoading).toBe(false);
    expect(result.current.resource.data).toBe('最新の内容');
    expect(result.current.writer.conflict).toBe(false);
    expect(send).not.toHaveBeenCalled();
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

  it.each([
    ['先に始めた方が先に終わる', [0, 1]],
    ['後に始めた方が先に終わる', [1, 0]],
  ])('重なった取り直しは、最後に始めたものの完了で競合を畳む（%s）', async (_, order) => {
    const finishes: (() => void)[] = [];
    const reloadTarget = jest.fn(() => new Promise<void>(resolve => finishes.push(resolve)));
    const { result } = renderHook(() => useVersionedWrite(reloadTarget));

    await act(async () => {
      await result.current.write(rejectWith(409), messages);
    });
    const reloads: Promise<void>[] = [];
    act(() => {
      reloads.push(result.current.reload(), result.current.reload());
    });
    await act(async () => {
      finishes[order[0]]();
      await reloads[order[0]];
    });
    expect(result.current.conflict).toBe(order[0] === 0);
    await act(async () => {
      finishes[order[1]]();
      await reloads[order[1]];
    });
    expect(result.current.conflict).toBe(false);
  });

  it('onSuccess で遷移する場合は、遷移が反映されるまで busy を残す', async () => {
    const { result } = setup();
    let arrive: () => void = () => {};
    const navigating = new Promise<void>(resolve => (arrive = resolve));
    // router.push と同じく何も返さず、自分の transition の中で画面を進める
    const push = () => {
      startTransition(async () => {
        await navigating;
      });
    };

    let outcome;
    await act(async () => {
      outcome = await result.current.write(async () => null, { ...messages, onSuccess: push });
    });
    expect(outcome).toBe('success');
    expect(result.current.busy).toBe(true);

    await act(async () => {
      arrive();
    });
    expect(result.current.busy).toBe(false);
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
