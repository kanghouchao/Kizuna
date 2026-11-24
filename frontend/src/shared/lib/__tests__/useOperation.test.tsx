import { act, renderHook } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { useOperation, useOperationRevision } from '../hooks/useOperation';
import {
  memoryOperationStore,
  sessionOperationStore,
  type OperationStore,
} from '../operation-store';

let userId: number | undefined = 7;
jest.mock('@/shared/api/session', () => ({ getSessionUserId: () => userId }));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

// 画面が表示している店舗（URL の店舗 id）。
let store = '1';
let sequence = 0;
const fail = (status?: number) =>
  Object.assign(new Error('failed'), status === undefined ? {} : { response: { status } });
const failure = '結果を確認できません';
const quiet = { failure };

function deferred<T = void>() {
  let resolve: (value: T) => void = () => {};
  let reject: (reason: unknown) => void = () => {};
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

beforeEach(() => {
  jest.clearAllMocks();
  userId = 7;
  store = '1';
});

const opened: {
  send: jest.Mock;
  current: () => ReturnType<typeof useOperation<{ amount: number }, string>>;
}[] = [];
function setup(operationStore: OperationStore = memoryOperationStore, name = `test-${++sequence}`) {
  const send = jest.fn();
  const hook = renderHook(() =>
    useOperation<{ amount: number }, string>(name, { storeId: store, store: operationStore, send })
  );
  const current = () => hook.result.current;
  opened.push({ send, current });
  return { send, name, hook, current };
}
// 結果不明の操作はモジュールの状態としてタブに残るので、用例ごとに成功させて片付ける。
afterEach(async () => {
  store = '1';
  userId = 7;
  for (const { send, current } of opened.splice(0)) {
    if (current().pending?.phase !== 'unknown') continue;
    send.mockResolvedValue('ok');
    await act(async () => {
      await current().replay(quiet);
    });
  }
  sessionStorage.clear();
});
async function attempt(run: () => Promise<unknown>) {
  let outcome: unknown;
  await act(async () => {
    outcome = await run();
  });
  return outcome;
}

// 業務拒否は初回・再送とも確定、認可拒否は初回だけ確定、それ以外は結果不明のまま（ADR 0037）。
describe.each([
  ['初回', false],
  ['再送', true],
])('%sの応答で結果不明を忘れるか', (_, replay) => {
  it.each([
    [400, true],
    [404, true],
    [409, true],
    [422, true],
    [401, !replay],
    [403, !replay],
    [500, false],
    [undefined, false],
  ])('%s → 忘れる: %s', async (status, settled) => {
    const { send, current } = setup();
    if (replay) {
      send.mockRejectedValueOnce(fail());
      await attempt(() => current().start({ amount: 1 }, quiet));
      expect(current().pending?.phase).toBe('unknown');
    }
    send.mockRejectedValueOnce(fail(status));
    const outcome = await attempt(() =>
      replay ? current().replay(quiet) : current().start({ amount: 1 }, quiet)
    );
    expect(outcome).toBe('failed');
    expect(current().pending === null).toBe(settled);
    if (!settled) expect(current().pending?.phase).toBe('unknown');
  });

  it('成功 → 忘れて、成功の案内を出し応答を反映する', async () => {
    const { send, current } = setup();
    if (replay) {
      send.mockRejectedValueOnce(fail());
      await attempt(() => current().start({ amount: 1 }, quiet));
    }
    send.mockResolvedValueOnce('ok');
    const onSuccess = jest.fn();
    const options = { success: '記録しました', failure, onSuccess };
    const outcome = await attempt(() =>
      replay ? current().replay(options) : current().start({ amount: 1 }, options)
    );
    expect(outcome).toBe('success');
    expect(notify.success).toHaveBeenCalledWith('記録しました');
    expect(onSuccess).toHaveBeenCalledWith('ok');
    expect(current().pending).toBeNull();
    expect(current().busy).toBe(false);
  });
});

it('再送は初回と同じキー・同じ内容を、起こした店舗に固定して送る', async () => {
  const { send, current } = setup();
  send.mockRejectedValueOnce(fail()).mockResolvedValueOnce('ok');
  await attempt(() => current().start({ amount: 1 }, quiet));
  await attempt(() => current().replay(quiet));
  const [first, second] = send.mock.calls;
  expect(first[1]).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  expect(second).toEqual(first);
  expect(first[2]).toEqual({ headers: { 'X-Store-ID': '1' } });
});

it('新しい操作は毎回別のキーを採番する', async () => {
  const { send, current } = setup();
  send.mockResolvedValue('ok');
  await attempt(() => current().start({ amount: 1 }, quiet));
  await attempt(() => current().start({ amount: 1 }, quiet));
  expect(send.mock.calls[0][1]).not.toBe(send.mock.calls[1][1]);
});

it('失敗は既定の文言で案内し、エラーを onFailure へ渡す', async () => {
  const { send, current } = setup();
  const error = fail(500);
  send.mockRejectedValueOnce(error);
  const onSuccess = jest.fn();
  const onFailure = jest.fn();
  const outcome = await attempt(() =>
    current().start({ amount: 1 }, { success: '記録しました', failure, onSuccess, onFailure })
  );
  expect(outcome).toBe('failed');
  expect(notify.error).toHaveBeenCalledWith(failure);
  expect(notify.success).not.toHaveBeenCalled();
  expect(onSuccess).not.toHaveBeenCalled();
  expect(onFailure).toHaveBeenCalledWith(error);
});

it('failure が関数ならエラーを渡して案内を任せる', async () => {
  const { send, current } = setup();
  const error = fail(422);
  send.mockRejectedValueOnce(error);
  const present = jest.fn();
  await attempt(() => current().start({ amount: 1 }, { failure: present }));
  expect(present).toHaveBeenCalledWith(error);
  expect(notify.error).not.toHaveBeenCalled();
});

it('送らなかった呼び出しは refused で、案内も反映もしない', async () => {
  const { send, current } = setup();
  const onSuccess = jest.fn();
  const onFailure = jest.fn();
  const failing = jest.fn();
  const outcome = await attempt(() =>
    current().replay({ success: '記録しました', failure: failing, onSuccess, onFailure })
  );
  expect(outcome).toBe('refused');
  expect(send).not.toHaveBeenCalled();
  expect(failing).not.toHaveBeenCalled();
  expect(notify.success).not.toHaveBeenCalled();
  expect(notify.error).not.toHaveBeenCalled();
  expect(onSuccess).not.toHaveBeenCalled();
  expect(onFailure).not.toHaveBeenCalled();
});

it('送信中の二度目は送らず、結果不明の間は新しい操作を起こさない', async () => {
  const { send, current } = setup();
  const response = deferred<string>();
  send.mockReturnValueOnce(response.promise);
  let first: Promise<unknown> = Promise.resolve();
  act(() => {
    first = current().start({ amount: 1 }, quiet);
  });
  expect(current().pending?.phase).toBe('submitting');
  expect(current().busy).toBe(true);
  const onSuccess = jest.fn();
  let second: unknown;
  let replayed: unknown;
  await act(async () => {
    second = await current().start({ amount: 2 }, { success: '記録しました', failure, onSuccess });
    replayed = await current().replay({ failure, onSuccess });
  });
  expect(second).toBe('refused');
  expect(replayed).toBe('refused');
  expect(onSuccess).not.toHaveBeenCalled();
  expect(notify.error).not.toHaveBeenCalled();
  await act(async () => {
    response.reject(fail());
    await first;
  });
  expect(current().pending?.phase).toBe('unknown');
  expect(await attempt(() => current().start({ amount: 3 }, quiet))).toBe('refused');
  expect(send).toHaveBeenCalledTimes(1);
  // 結果不明の後に送れるのは再送だけで、初回と同じキー・同じ内容になる
  send.mockResolvedValueOnce('ok');
  expect(await attempt(() => current().replay(quiet))).toBe('success');
  expect(send).toHaveBeenCalledTimes(2);
  expect(send.mock.calls[1]).toEqual(send.mock.calls[0]);
  expect(send.mock.calls[1][0]).toEqual({ amount: 1 });
});

it('応答の後も反映が終わるまで busy で、二度目は送らない', async () => {
  const { send, current } = setup();
  send.mockResolvedValue('ok');
  const reflection = deferred();
  let first: Promise<unknown> = Promise.resolve();
  await act(async () => {
    first = current().start({ amount: 1 }, { failure, onSuccess: () => reflection.promise });
  });
  // 応答で操作は片付いているが、反映はまだ終わっていない
  expect(send).toHaveBeenCalledTimes(1);
  expect(current().pending).toBeNull();
  expect(current().busy).toBe(true);
  let second: unknown;
  await act(async () => {
    second = await current().start({ amount: 2 }, quiet);
  });
  expect(second).toBe('refused');
  expect(send).toHaveBeenCalledTimes(1);
  await act(async () => {
    reflection.resolve();
    await first;
  });
  expect(current().busy).toBe(false);
});

it('同じ操作を持つ別の箇所も、送信中は busy になる', async () => {
  const { send, current, name } = setup();
  const other = renderHook(() =>
    useOperation<{ amount: number }, string>(name, {
      storeId: '1',
      store: memoryOperationStore,
      send,
    })
  );
  const response = deferred<string>();
  send.mockReturnValueOnce(response.promise);
  let first: Promise<unknown> = Promise.resolve();
  act(() => {
    first = current().start({ amount: 1 }, quiet);
  });
  expect(other.result.current.busy).toBe(true);
  await act(async () => {
    response.resolve('ok');
    await first;
  });
  expect(other.result.current.busy).toBe(false);
});

it('画面が外れた後の応答は案内だけ出し、反映しない', async () => {
  const { send, current, hook } = setup();
  const response = deferred<string>();
  send.mockReturnValueOnce(response.promise);
  const onSuccess = jest.fn();
  let first: Promise<unknown> = Promise.resolve();
  act(() => {
    first = current().start({ amount: 1 }, { success: '記録しました', failure, onSuccess });
  });
  hook.unmount();
  await act(async () => {
    response.resolve('ok');
    await first;
  });
  expect(notify.success).toHaveBeenCalledWith('記録しました');
  expect(onSuccess).not.toHaveBeenCalled();
});

it('起こした後に本人が変わったら送らずに失敗として案内する', async () => {
  const { send, current, hook } = setup();
  send.mockRejectedValueOnce(fail());
  await attempt(() => current().start({ amount: 1 }, quiet));
  const { replay } = current();
  userId = 8;
  const onFailure = jest.fn();
  expect(await attempt(() => replay({ failure, onFailure }))).toBe('failed');
  expect(notify.error).toHaveBeenLastCalledWith(
    'ログイン中のアカウントか店舗が変わりました。画面を開き直してください。'
  );
  expect(send).toHaveBeenCalledTimes(1);
  // 元の本人に戻れば、結果不明の操作はそのまま残っている
  userId = 7;
  hook.rerender();
  expect(current().pending?.phase).toBe('unknown');
});

it('本人か店舗が確定するまで操作を起こさない', async () => {
  userId = undefined;
  const { send, current } = setup();
  expect(current().ready).toBe(false);
  expect(await attempt(() => current().start({ amount: 1 }, quiet))).toBe('refused');
  expect(send).not.toHaveBeenCalled();
});

it('結果不明の操作がある間はタブを閉じる前に確認する', async () => {
  const { send, current } = setup();
  send.mockRejectedValueOnce(fail()).mockResolvedValueOnce('ok');
  await attempt(() => current().start({ amount: 1 }, quiet));
  const blocked = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(blocked);
  expect(blocked.defaultPrevented).toBe(true);
  await attempt(() => current().replay(quiet));
  const released = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(released);
  expect(released.defaultPrevented).toBe(false);
});

it('タブ内保存の操作は画面を開き直しても結果不明として残り、操作の版で購読側に知らせる', async () => {
  const { send, current, name, hook } = setup(sessionOperationStore);
  const revision = renderHook(() => useOperationRevision(name, '1'));
  send.mockRejectedValueOnce(fail());
  await attempt(() => current().start({ amount: 1 }, quiet));
  expect(revision.result.current).toBe(2);
  hook.unmount();
  const reopened = renderHook(() =>
    useOperation<{ amount: number }, string>(name, {
      storeId: '1',
      store: sessionOperationStore,
      send,
    })
  );
  expect(reopened.result.current.pending).toMatchObject({
    phase: 'unknown',
    payload: { amount: 1 },
  });
});

it('タブ内保存の値が読めなければ新しい操作を止める', async () => {
  const name = `test-${++sequence}`;
  sessionStorage.setItem(`pending-operation:${JSON.stringify([name, 7, '1'])}`, '{"broken"');
  const { send, current } = setup(sessionOperationStore, name);
  expect(current().unreadable).toBe(true);
  expect(await attempt(() => current().start({ amount: 1 }, quiet))).toBe('failed');
  expect(notify.error).toHaveBeenCalled();
  expect(send).not.toHaveBeenCalled();
});
