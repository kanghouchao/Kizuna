import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Order, orderApi } from '@/entities/order';
import { useVersionedWrite } from '@/shared/lib';
import type { KeyedResource } from '@/shared/lib';
import { OrderServiceProgress } from '../ui/OrderServiceProgress';
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useCurrentStoreId: () => '1',
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
  }),
}));

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: { specialServiceEvents: jest.fn(), start: jest.fn() },
}));
type Reload = KeyedResource<Order>['reload'];
/** 編集画面と同じく、書き込みを親で作って渡す。取り直しは共有の書き込みを通す */
function Harness({
  order,
  onOrderUpdated,
  onReload = jest.fn(),
  stale = false,
  onStale = jest.fn(),
}: {
  order: Order;
  onOrderUpdated: (order: Order) => void;
  onReload?: Reload;
  stale?: boolean;
  onStale?: () => void;
}) {
  const writer = useVersionedWrite(onReload);
  return (
    <>
      <button type="button" onClick={() => void writer.reload()}>
        読み込む
      </button>
      <OrderServiceProgress
        order={order}
        writer={writer}
        stale={stale}
        onStale={onStale}
        onOrderUpdated={onOrderUpdated}
        onReload={writer.reload}
      />
    </>
  );
}
const order = {
  id: 'o1',
  version: 1,
  status: 'CONFIRMED',
  requires_attention: false,
} as Order;
beforeEach(() => {
  jest.clearAllMocks();
  (orderApi.specialServiceEvents as jest.Mock).mockResolvedValue({
    rows: [],
    page: 0,
    pageCount: 2,
    total: 21,
  });
});
test('空の開始理由は送信時にエラーを示して入力へフォーカスする', async () => {
  render(<Harness order={order} onOrderUpdated={jest.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  expect(screen.getByRole('alert')).toHaveTextContent('開始の理由を入力してください');
  expect(screen.getByLabelText('開始の理由')).toHaveFocus();
  expect(orderApi.start).not.toHaveBeenCalled();
  await screen.findByText('拒否・処置の記録はありません。');
});
test('開始中の履歴ページ変更でも更新応答を反映し、送信中の状態を解除する', async () => {
  let finish!: (value: Order) => void;
  (orderApi.start as jest.Mock).mockReturnValue(
    new Promise<Order>(resolve => {
      finish = resolve;
    })
  );
  const onOrderUpdated = jest.fn();
  render(<Harness order={order} onOrderUpdated={onOrderUpdated} />);
  await waitFor(() => expect(screen.getAllByRole('button', { name: '次へ' })[0]).toBeEnabled());
  fireEvent.change(screen.getByLabelText('開始の理由'), { target: { value: '提供開始' } });
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
  await waitFor(() => expect(orderApi.specialServiceEvents).toHaveBeenCalledWith('o1', 1));
  const updated = { ...order, status: 'IN_SERVICE' as const, version: 2 };
  await act(async () => finish(updated));
  expect(onOrderUpdated).toHaveBeenCalledWith(updated);
  expect(screen.getByRole('button', { name: 'サービスを開始' })).toBeEnabled();
});

test('開始の版競合では取り直さずに画面へ伝え、最新を読み込むまで開始を塞ぐ', async () => {
  const conflict = {
    isAxiosError: true,
    response: { status: 409, data: { details: { expected_version: '競合' } } },
  };
  (orderApi.start as jest.Mock)
    .mockRejectedValueOnce(conflict)
    .mockResolvedValue({ ...order, version: 3, status: 'IN_SERVICE' });
  const latest = { ...order, version: 2 };
  const onReload = jest.fn<ReturnType<Reload>, []>(async () => ({
    status: 'success' as const,
    data: latest,
    isCurrent: () => true,
  }));
  const onOrderUpdated = jest.fn();
  const onStale = jest.fn();
  const props = { onOrderUpdated, onReload, onStale };
  const view = render(<Harness order={order} {...props} />);
  fireEvent.change(screen.getByLabelText('開始の理由'), { target: { value: '提供開始' } });
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  await waitFor(() => expect(onStale).toHaveBeenCalledTimes(1));
  expect(onReload).not.toHaveBeenCalled();
  expect(onOrderUpdated).not.toHaveBeenCalled();
  view.rerender(<Harness order={order} {...props} stale />);
  expect(screen.getByRole('button', { name: 'サービスを開始' })).toBeDisabled();
  // 画面が最新を読み込むと、共有の書き込みの競合が畳まれ、版が替わる
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '読み込む' }));
  });
  view.rerender(<Harness order={latest} {...props} />);
  expect(screen.getByLabelText('開始の理由')).toHaveValue('提供開始');
  fireEvent.click(await screen.findByRole('button', { name: 'サービスを開始' }));
  await waitFor(() => expect(orderApi.start).toHaveBeenLastCalledWith('o1', 2, '提供開始'));
});

test('業務の競合ではサーバの文言を節に出し、取り直すまで開始を塞ぐ', async () => {
  (orderApi.start as jest.Mock).mockRejectedValue({
    isAxiosError: true,
    response: { status: 409, data: { error: '本人拒否の処置が必要です' } },
  });
  let settle!: () => void;
  const onReload = jest.fn<ReturnType<Reload>, []>(
    () =>
      new Promise(resolve => {
        settle = () => resolve({ status: 'success', data: order, isCurrent: () => true });
      })
  );
  render(<Harness order={order} onOrderUpdated={jest.fn()} onReload={onReload} />);
  fireEvent.change(screen.getByLabelText('開始の理由'), { target: { value: '開始' } });
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  await screen.findByText('本人拒否の処置が必要です');
  await waitFor(() => expect(onReload).toHaveBeenCalledTimes(1));
  expect(screen.getByRole('button', { name: 'サービスを開始' })).toBeDisabled();
  await act(async () => settle());
  expect(await screen.findByRole('button', { name: 'サービスを開始' })).toBeEnabled();
});
