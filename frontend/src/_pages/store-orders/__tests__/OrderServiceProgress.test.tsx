import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Order, orderApi } from '@/entities/order';
import { OrderServiceProgress } from '../ui/OrderServiceProgress';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: { specialServiceEvents: jest.fn(), start: jest.fn() },
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
  render(<OrderServiceProgress order={order} onStarted={jest.fn()} />);
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
  const onStarted = jest.fn();
  render(<OrderServiceProgress order={order} onStarted={onStarted} />);
  await waitFor(() => expect(screen.getByRole('button', { name: '次の履歴' })).toBeEnabled());
  fireEvent.change(screen.getByLabelText('開始の理由'), { target: { value: '提供開始' } });
  fireEvent.click(screen.getByRole('button', { name: 'サービスを開始' }));
  fireEvent.click(screen.getByRole('button', { name: '次の履歴' }));
  await waitFor(() => expect(orderApi.specialServiceEvents).toHaveBeenCalledWith('o1', 'next'));
  const updated = { ...order, status: 'IN_SERVICE' as const, version: 2 };
  await act(async () => finish(updated));
  expect(onStarted).toHaveBeenCalledWith(updated);
  expect(screen.getByRole('button', { name: 'サービスを開始' })).toBeEnabled();
});
