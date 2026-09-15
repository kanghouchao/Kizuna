import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Order, orderApi } from '@/entities/order';
import { OrderServiceProgress } from '../ui/OrderServiceProgress';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: { specialServiceEvents: jest.fn(), start: jest.fn(), get: jest.fn() },
}));
const order = {
  id: 'o1',
  version: 1,
  status: 'CONFIRMED',
  requires_attention: false,
} as Order;
beforeEach(() => {
  jest.clearAllMocks();
  (orderApi.specialServiceEvents as jest.Mock).mockResolvedValue({ rows: [], nextCursor: 'next' });
});
test('空の開始理由は送信時にエラーを示して入力へフォーカスする', async () => {
  render(<OrderServiceProgress order={order} onOrderUpdated={jest.fn()} />);
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
  render(<OrderServiceProgress order={order} onOrderUpdated={onOrderUpdated} />);
  await waitFor(() => expect(screen.getByRole('button', { name: '次の履歴' })).toBeEnabled());
  fireEvent.change(screen.getByLabelText('開始の理由'), { target: { value: '提供開始' } });
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  fireEvent.click(screen.getByRole('button', { name: '次の履歴' }));
  await waitFor(() => expect(orderApi.specialServiceEvents).toHaveBeenCalledWith('o1', 'next'));
  const updated = { ...order, status: 'IN_SERVICE' as const, version: 2 };
  await act(async () => finish(updated));
  expect(onOrderUpdated).toHaveBeenCalledWith(updated);
  expect(screen.getByRole('button', { name: 'サービスを開始' })).toBeEnabled();
});

test('開始の版競合では最新の受注を反映して新しい版で再試行できる', async () => {
  const conflict = {
    isAxiosError: true,
    response: { status: 409, data: { details: { expected_version: '競合' } } },
  };
  (orderApi.start as jest.Mock)
    .mockRejectedValueOnce(conflict)
    .mockResolvedValue({ ...order, version: 3, status: 'IN_SERVICE' });
  const latest = { ...order, version: 2 };
  (orderApi.get as jest.Mock).mockResolvedValue(latest);
  const onOrderUpdated = jest.fn();
  const view = render(<OrderServiceProgress order={order} onOrderUpdated={onOrderUpdated} />);
  fireEvent.change(screen.getByLabelText('開始の理由'), { target: { value: '提供開始' } });
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  await waitFor(() => expect(onOrderUpdated).toHaveBeenCalledWith(latest));
  expect(orderApi.get).toHaveBeenCalledWith('o1');
  view.rerender(<OrderServiceProgress order={latest} onOrderUpdated={onOrderUpdated} />);
  expect(screen.getByLabelText('開始の理由')).toHaveValue('提供開始');
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  await waitFor(() => expect(orderApi.start).toHaveBeenLastCalledWith('o1', 2, '提供開始'));
});

test('版競合後の再取得に失敗しても再試行で復旧できる', async () => {
  (orderApi.start as jest.Mock).mockRejectedValue({
    isAxiosError: true,
    response: { status: 409, data: { details: { expected_version: '競合' } } },
  });
  (orderApi.get as jest.Mock)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValue({ ...order, version: 2 });
  const onOrderUpdated = jest.fn();
  render(<OrderServiceProgress order={order} onOrderUpdated={onOrderUpdated} />);
  fireEvent.change(screen.getByLabelText('開始の理由'), { target: { value: '開始' } });
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  await screen.findByText('最新の受注を取得できませんでした。開始を再試行して再取得してください。');
  expect(onOrderUpdated).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  await waitFor(() => expect(onOrderUpdated).toHaveBeenCalledWith({ ...order, version: 2 }));
});
