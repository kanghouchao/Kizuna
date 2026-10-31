import { authApi } from '@/entities/user';
import { advertisingApi, type Operation } from '../api/advertising';
import { operationKey, readPending, requestId, submitOperation } from './operation';
jest.mock('../api/advertising', () => ({ advertisingApi: { mutate: jest.fn() } }));
jest.mock('@/entities/user', () => ({ authApi: { me: jest.fn() } }));
const mutate = jest.mocked(advertisingApi.mutate);
const me = jest.mocked(authApi.me);
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
  me.mockResolvedValue({ email: 'user', line_linked: false });
});
it('別タブで別のアカウントに切り替わっていたら送らず、何も預けない', async () => {
  // 画面が持つ操作者は layout の生存期間の値で、token は別タブのログインで差し替わる。送る直前に
  // 今の token の持ち主を確かめないと、前の操作者の入力が別の利用者の操作として記録される。
  me.mockResolvedValue({ email: 'other', line_linked: false });
  await expect(submitOperation(op, false, jest.fn())).rejects.toThrow('アカウント');
  expect(mutate).not.toHaveBeenCalled();
  expect(readPending(key)).toBeNull();
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
it('本人確認を待つ間の二重送信も、先の送信が終わった後に送らない', async () => {
  // 確認中に作用域を押さえないと、先の送信が終わってから後の確認が返り、別の request_id で二度書く
  let answer!: (v: { email: string; line_linked: boolean }) => void;
  me.mockImplementationOnce(() => new Promise(r => (answer = r)));
  mutate.mockResolvedValue({} as never);
  const first = submitOperation(op, false, jest.fn());
  const second = submitOperation({ ...op, request_id: requestId() }, false, jest.fn());
  answer({ email: 'user', line_linked: false });
  expect(await first).toBe(true);
  expect(await second).toBe(false);
  expect(mutate).toHaveBeenCalledTimes(1);
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
  // 送る前の本人確認（/me）を待ってから送信中になる
  await new Promise(process.nextTick);
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
