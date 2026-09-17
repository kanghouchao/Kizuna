import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { orderApi } from '@/entities/order';
import { hasPermission } from '@/shared/lib';
import OrderCompletionInvalidationPage from '../ui/OrderCompletionInvalidationPage';

jest.mock('@/entities/order', () => ({
  orderApi: { get: jest.fn(), invalidateCompletion: jest.fn(), correctionHistory: jest.fn() },
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  hasPermission: jest.fn(() => true),
}));
let mockParams = { storeId: '1', id: 'o1' };
jest.mock('next/navigation', () => ({ useParams: () => mockParams }));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
const order = {
  id: 'o1',
  status: 'COMPLETED',
  version: 4,
  completion_invalidated: false,
  business_date: '2026-09-14',
  completed_at: '2026-09-15T00:00:00Z',
  total_fee: 10000,
  accrued_remuneration: 6000,
  course: { name: 'コース' },
  fee_lines: [],
};
const change = {
  correction_id: 'change-1',
  change_type: 'COMPLETION_INVALIDATION',
  before: { total_fee: 9000, accrued_remuneration: 5000 },
  after: { total_fee: 0, accrued_remuneration: 0 },
};
beforeEach(() => {
  jest.clearAllMocks();
  mockParams = { storeId: '1', id: 'o1' };
  (hasPermission as jest.Mock).mockReturnValue(true);
  (orderApi.get as jest.Mock).mockResolvedValue(order);
  (orderApi.invalidateCompletion as jest.Mock).mockResolvedValue(change);
  (orderApi.correctionHistory as jest.Mock).mockResolvedValue({ rows: [change], nextCursor: null });
});
test('未提供と理由を確認し、表示した版で無効化する', async () => {
  render(<OrderCompletionInvalidationPage />);
  fireEvent.change(await screen.findByLabelText('理由'), { target: { value: '提供前の誤完了' } });
  fireEvent.click(screen.getByRole('button', { name: '無効化を確認' }));
  expect(orderApi.invalidateCompletion).not.toHaveBeenCalled();
  expect(await screen.findByText(/全く提供していない/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '未提供を確認して無効化' }));
  await waitFor(() =>
    expect(orderApi.invalidateCompletion).toHaveBeenCalledWith('o1', {
      expected_version: 4,
      reason: '提供前の誤完了',
    })
  );
  expect(await screen.findByText(/変更 ID：change-1/)).toBeInTheDocument();
  expect(await screen.findByText('有効な請求 9,000 円 → 0 円')).toBeInTheDocument();
  expect(screen.getByText('発生済み報酬 5,000 円 → 0 円')).toBeInTheDocument();
});

test('利用が残る場合は救済担当者を示し無効化を送れない', async () => {
  (hasPermission as jest.Mock).mockImplementation((_, code) => code !== 'POINT_ADJUST');
  (orderApi.get as jest.Mock).mockResolvedValue({
    ...order,
    fee_lines: [{ kind: 'POINT_REDEMPTION', amount: 3000, remuneration: 0 }],
  });
  render(<OrderCompletionInvalidationPage />);
  expect(await screen.findByText(/POINT_ADJUST を持つポイント救済担当者/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '無効化を確認' })).not.toBeInTheDocument();
  expect(orderApi.invalidateCompletion).not.toHaveBeenCalled();
});

test('権限不足で受注を取得せず必要な担当者を示す', async () => {
  (hasPermission as jest.Mock).mockReturnValue(false);
  render(<OrderCompletionInvalidationPage />);
  expect(await screen.findByText(/ORDER_CORRECT を持つ訂正担当者/)).toBeInTheDocument();
  expect(orderApi.get).not.toHaveBeenCalled();
});

test('取得失敗は再試行でき、店舗切替で旧記録を隠す', async () => {
  (orderApi.get as jest.Mock)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce(order)
    .mockReturnValue(new Promise(() => {}));
  const view = render(<OrderCompletionInvalidationPage />);
  await screen.findByText('受注を取得できませんでした。');
  fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
  await screen.findByText('コース');
  mockParams = { storeId: '2', id: 'o1' };
  view.rerender(<OrderCompletionInvalidationPage />);
  expect(screen.queryByText('コース')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('理由')).not.toBeInTheDocument();
});

test('競合では自動再送せず更新された版を再確認する', async () => {
  (orderApi.invalidateCompletion as jest.Mock).mockRejectedValueOnce({
    response: { status: 409, data: { error: '別の操作者が更新しました' } },
  });
  (orderApi.get as jest.Mock)
    .mockResolvedValueOnce(order)
    .mockResolvedValue({ ...order, version: 6, total_fee: 8000 });
  render(<OrderCompletionInvalidationPage />);
  fireEvent.change(await screen.findByLabelText('理由'), { target: { value: '未提供' } });
  fireEvent.click(screen.getByRole('button', { name: '無効化を確認' }));
  fireEvent.click(await screen.findByRole('button', { name: '未提供を確認して無効化' }));
  await screen.findByText(/別の操作者が更新しました/);
  await screen.findByText(/8,000 円 → 0 円/);
  expect(orderApi.invalidateCompletion).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button', { name: '未提供を確認して無効化' })).not.toBeInTheDocument();
});

test('ORDER_MANAGE だけでも無効化済み受注から再提供できる', async () => {
  (hasPermission as jest.Mock).mockImplementation((_, code) => code === 'ORDER_MANAGE');
  (orderApi.get as jest.Mock).mockResolvedValue({
    ...order,
    completion_invalidated: true,
    total_fee: 0,
    accrued_remuneration: 0,
  });
  render(<OrderCompletionInvalidationPage />);
  const link = await screen.findByRole('link', { name: '関連する新受注で再提供' });
  expect(link).toHaveAttribute('href', '/store/1/orders/create?replacement_for_order_id=o1');
  expect(screen.queryByLabelText('理由')).not.toBeInTheDocument();
});

test('再訪では無効化履歴の前後額を表示する', async () => {
  (orderApi.get as jest.Mock).mockResolvedValue({
    ...order,
    completion_invalidated: true,
    total_fee: 0,
    accrued_remuneration: 0,
  });
  render(<OrderCompletionInvalidationPage />);
  expect(await screen.findByText('有効な請求 9,000 円 → 0 円')).toBeInTheDocument();
  expect(screen.getByText('発生済み報酬 5,000 円 → 0 円')).toBeInTheDocument();
  expect(orderApi.correctionHistory).toHaveBeenCalledWith('store', 'o1');
});

test('再訪時の履歴取得失敗を零の前値で隠さず再試行できる', async () => {
  (orderApi.get as jest.Mock).mockResolvedValue({
    ...order,
    completion_invalidated: true,
    total_fee: 0,
    accrued_remuneration: 0,
  });
  (orderApi.correctionHistory as jest.Mock).mockRejectedValueOnce(new Error('offline'));
  render(<OrderCompletionInvalidationPage />);
  await screen.findByText('受注を取得できませんでした。');
  expect(screen.queryByText(/0 円 → 0 円/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
  expect(await screen.findByText('有効な請求 9,000 円 → 0 円')).toBeInTheDocument();
});
