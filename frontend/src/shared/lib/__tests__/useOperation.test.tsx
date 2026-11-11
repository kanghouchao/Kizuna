import { act, renderHook } from '@testing-library/react';
import { useOperation, useOperationRevision } from '../hooks/useOperation';
import {
  memoryOperationStore,
  sessionOperationStore,
  type OperationStore,
} from '../operation-store';
import { ClientDataError } from '../apiError';

let userId: number | undefined = 7;
jest.mock('@/shared/api/session', () => ({ getSessionUserId: () => userId }));

// 画面が表示している店舗（URL の店舗 id）。
let store = '1';
let sequence = 0;
const fail = (status?: number) =>
  Object.assign(new Error('failed'), status === undefined ? {} : { response: { status } });

beforeEach(() => {
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
      await current().replay();
    });
  }
  sessionStorage.clear();
});
async function attempt(run: () => Promise<unknown>) {
  let error: unknown;
  await act(async () => {
    try {
      await run();
    } catch (caught) {
      error = caught;
    }
  });
  return error;
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
      await attempt(() => current().start({ amount: 1 }));
      expect(current().pending?.phase).toBe('unknown');
    }
    send.mockRejectedValueOnce(fail(status));
    const error = await attempt(() =>
      replay ? current().replay() : current().start({ amount: 1 })
    );
    expect(error).toBeDefined();
    expect(current().pending === null).toBe(settled);
    if (!settled) expect(current().pending?.phase).toBe('unknown');
  });

  it('成功 → 忘れて応答を返す', async () => {
    const { send, current } = setup();
    if (replay) {
      send.mockRejectedValueOnce(fail());
      await attempt(() => current().start({ amount: 1 }));
    }
    send.mockResolvedValueOnce('ok');
    let result: unknown;
    await act(async () => {
      result = await (replay ? current().replay() : current().start({ amount: 1 }));
    });
    expect(result).toEqual({ value: 'ok' });
    expect(current().pending).toBeNull();
  });
});

it('再送は初回と同じキー・同じ内容を、起こした店舗に固定して送る', async () => {
  const { send, current } = setup();
  send.mockRejectedValueOnce(fail()).mockResolvedValueOnce('ok');
  await attempt(() => current().start({ amount: 1 }));
  await attempt(() => current().replay());
  const [first, second] = send.mock.calls;
  expect(first[1]).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  expect(second).toEqual(first);
  expect(first[2]).toEqual({ headers: { 'X-Store-ID': '1' } });
});

it('新しい操作は毎回別のキーを採番する', async () => {
  const { send, current } = setup();
  send.mockResolvedValue('ok');
  await attempt(() => current().start({ amount: 1 }));
  await attempt(() => current().start({ amount: 1 }));
  expect(send.mock.calls[0][1]).not.toBe(send.mock.calls[1][1]);
});

it('同じ操作は単飛し、結果不明の間は新しい操作を起こさない', async () => {
  const { send, current } = setup();
  let reject: (error: unknown) => void = () => undefined;
  send.mockReturnValueOnce(new Promise((_, r) => (reject = r)));
  let first: Promise<unknown> = Promise.resolve();
  act(() => {
    first = current()
      .start({ amount: 1 })
      .catch(() => undefined);
  });
  expect(current().pending?.phase).toBe('submitting');
  let second: unknown;
  let replayed: unknown;
  await act(async () => {
    second = await current().start({ amount: 2 });
    replayed = await current().replay();
  });
  expect(second).toBeNull();
  expect(replayed).toBeNull();
  await act(async () => {
    reject(fail());
    await first;
  });
  expect(current().pending?.phase).toBe('unknown');
  expect(await current().start({ amount: 3 })).toBeNull();
  expect(send).toHaveBeenCalledTimes(1);
});

it('起こした後に本人が変わったら送らずに止める', async () => {
  const { send, current, hook } = setup();
  send.mockRejectedValueOnce(fail());
  await attempt(() => current().start({ amount: 1 }));
  const { replay } = current();
  userId = 8;
  const error = await attempt(replay);
  expect(error).toBeInstanceOf(ClientDataError);
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
  expect(await current().start({ amount: 1 })).toBeNull();
  expect(send).not.toHaveBeenCalled();
});

it('結果不明の操作がある間はタブを閉じる前に確認する', async () => {
  const { send, current } = setup();
  send.mockRejectedValueOnce(fail()).mockResolvedValueOnce('ok');
  await attempt(() => current().start({ amount: 1 }));
  const blocked = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(blocked);
  expect(blocked.defaultPrevented).toBe(true);
  await attempt(() => current().replay());
  const released = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(released);
  expect(released.defaultPrevented).toBe(false);
});

it('タブ内保存の操作は画面を開き直しても結果不明として残り、操作の版で購読側に知らせる', async () => {
  const { send, current, name, hook } = setup(sessionOperationStore);
  const revision = renderHook(() => useOperationRevision(name, '1'));
  send.mockRejectedValueOnce(fail());
  await attempt(() => current().start({ amount: 1 }));
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
  expect(await attempt(() => current().start({ amount: 1 }))).toBeDefined();
  expect(send).not.toHaveBeenCalled();
});
