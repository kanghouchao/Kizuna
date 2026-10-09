import { advertisingApi, type Operation } from '../api/advertising';
import { operationKey, readPending, requestId, submitOperation } from './operation';
jest.mock('../api/advertising', () => ({ advertisingApi: { mutate: jest.fn() } }));
const mutate = jest.mocked(advertisingApi.mutate);
const op: Operation = {
  subject: 'user',
  store: '1',
  month: '2026-10',
  request_id: 'ba0d6746-63cc-4772-a590-7e7eaa392518',
  mutation: {
    kind: 'create',
    values: {
      category: 'SALES',
      media_name: '媒体',
      agency_name: null,
      plan_name: null,
      inquiry_count: null,
      amount: 100,
    },
  },
};
const key = operationKey(op.subject, op.store, op.month);
beforeEach(() => {
  sessionStorage.clear();
  jest.clearAllMocks();
});
it('応答不明の内容を保持し、再読込後も同じ要求だけを再送する', async () => {
  mutate.mockRejectedValueOnce(new Error('通信断')).mockResolvedValueOnce({} as never);
  await expect(submitOperation(op, false, jest.fn())).rejects.toThrow('通信断');
  expect(readPending(key)).toEqual({ operation: op, phase: 'unknown' });
  expect(await submitOperation({ ...op, request_id: requestId() }, false, jest.fn())).toBe(false);
  expect(await submitOperation(readPending(key)!.operation, true, jest.fn())).toBe(true);
  expect(mutate.mock.calls).toEqual([[op], [op]]);
  expect(readPending(key)).toBeNull();
});
it('送信中の閉じる・再表示・二重送信でも一度だけ送信する', async () => {
  let resolve!: (v: never) => void;
  mutate.mockImplementationOnce(
    () =>
      new Promise(r => {
        resolve = r;
      })
  );
  const first = submitOperation(op, false, jest.fn());
  expect(readPending(key)?.phase).toBe('submitting');
  expect(await submitOperation(op, true, jest.fn())).toBe(false);
  expect(await submitOperation(op, false, jest.fn())).toBe(false);
  resolve({} as never);
  await first;
  expect(mutate).toHaveBeenCalledTimes(1);
});
it('結果不明後の権限失効は既存の操作を消さず、初回の確定した拒否だけ解除する', async () => {
  mutate.mockRejectedValue({ response: { status: 403 } });
  await expect(submitOperation(op, false, jest.fn())).rejects.toBeDefined();
  expect(readPending(key)).toBeNull();
  sessionStorage.setItem(key, JSON.stringify(op));
  await expect(submitOperation(op, true, jest.fn())).rejects.toBeDefined();
  expect(readPending(key)?.phase).toBe('unknown');
});
it('別店舗・別月・別主体の操作を混ぜず、保存不可なら送信しない', async () => {
  sessionStorage.setItem(key, JSON.stringify(op));
  expect(readPending(operationKey('user', '2', '2026-10'))).toBeNull();
  expect(readPending(operationKey('user', '1', '2026-11'))).toBeNull();
  expect(readPending(operationKey('other', '1', '2026-10'))).toBeNull();
  sessionStorage.clear();
  const storage = jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
    throw new Error('保存不可');
  });
  await expect(submitOperation(op, false, jest.fn())).rejects.toThrow('保存不可');
  expect(mutate).not.toHaveBeenCalled();
  storage.mockRestore();
});

it('結果不明でも成功受領が存在しない確定競合は解除して再入力できる', async () => {
  sessionStorage.setItem(key, JSON.stringify(op));
  mutate.mockRejectedValue({ response: { status: 409 } });
  await expect(submitOperation(op, true, jest.fn())).rejects.toBeDefined();
  expect(readPending(key)).toBeNull();
});
it('完了イベントに元の作用域を含め、戻った画面が再取得できる', async () => {
  mutate.mockResolvedValue({} as never);
  const listener = jest.fn();
  window.addEventListener('advertising-operation', listener);
  await submitOperation(op, false, jest.fn());
  expect(listener.mock.calls.at(-1)![0].detail).toEqual({ key, completed: true });
  window.removeEventListener('advertising-operation', listener);
});
