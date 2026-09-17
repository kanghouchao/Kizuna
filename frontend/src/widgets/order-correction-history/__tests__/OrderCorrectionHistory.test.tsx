import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { orderApi, OrderCorrectionHistoryEntry } from '@/entities/order';
import { OrderCorrectionHistory } from '../ui/OrderCorrectionHistory';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: { correctionHistory: jest.fn() },
}));
const fetchHistory = jest.mocked(orderApi.correctionHistory);
const snapshot = {
  completion_invalidated: false,
  course: {
    service_id: 's1',
    revision_id: 'r1',
    revision_number: 1,
    name: '歴史コース',
    duration_minutes: 60,
    price: 12000,
    remuneration: 7000,
    adoption_basis: 'HISTORICAL_CORRECTION' as const,
    adopted_at: '2026-09-16T09:00:00+09:00',
  },
  fee_lines: [
    {
      line_id: 'l1',
      kind: 'BASE_COURSE' as const,
      name: '歴史コース',
      amount: 12000,
      remuneration: 7000,
      system_owned: false,
    },
  ],
  special_services: [],
  total_fee: 12000,
  total_duration_minutes: 60,
  total_remuneration: 7000,
  accrued_remuneration: 7000,
};
const entry: OrderCorrectionHistoryEntry = {
  change_type: 'CORRECTION',
  correction_id: 'c1',
  order_id: 'o1',
  store_id: 1,
  reason: '提供コースの訂正',
  corrected_by: 7,
  business_date: '2026-09-14',
  completed_at: '2026-09-15T01:00:00+09:00',
  corrected_at: '2026-09-16T09:00:00+09:00',
  before_version: 2,
  after_version: 3,
  before: snapshot,
  after: { ...snapshot, total_fee: 11000 },
};
beforeEach(() => jest.clearAllMocks());
it('前後の費用・報酬と理由を表示し、追加取得の失敗では古い履歴を消して先頭から再試行する', async () => {
  fetchHistory
    .mockResolvedValueOnce({ rows: [entry], nextCursor: 'next' })
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce({ rows: [entry], nextCursor: null });
  render(<OrderCorrectionHistory orderId="o1" scope="store" />);
  expect(await screen.findByText('提供コースの訂正')).toBeInTheDocument();
  expect(screen.getByText('請求 ¥12,000 → ¥11,000')).toBeInTheDocument();
  expect(screen.getByText('発生済み報酬 ¥7,000 → ¥7,000')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'さらに表示' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('履歴を取得できませんでした');
  expect(screen.queryByText('提供コースの訂正')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  expect(await screen.findByText('提供コースの訂正')).toBeInTheDocument();
  await waitFor(() => expect(fetchHistory).toHaveBeenLastCalledWith('store', 'o1', undefined));
});
it('平台の権限不足は空履歴と区別する', async () => {
  fetchHistory.mockRejectedValue({ response: { status: 403 } });
  render(<OrderCorrectionHistory orderId="o1" scope="platform" />);
  expect(await screen.findByRole('alert')).toHaveTextContent('履歴を閲覧する権限がありません');
  expect(screen.queryByText('訂正履歴はありません')).not.toBeInTheDocument();
});

it('対象切替後に旧対象の遅い応答を表示しない', async () => {
  let resolveOld!: (value: { rows: OrderCorrectionHistoryEntry[]; nextCursor: null }) => void;
  fetchHistory
    .mockReturnValueOnce(
      new Promise(resolve => {
        resolveOld = resolve;
      })
    )
    .mockResolvedValueOnce({ rows: [], nextCursor: null });
  const { rerender } = render(<OrderCorrectionHistory key="o1" orderId="o1" scope="store" />);
  rerender(<OrderCorrectionHistory key="o2" orderId="o2" scope="store" />);
  expect(await screen.findByText('訂正履歴はありません')).toBeInTheDocument();
  await act(async () => resolveOld({ rows: [entry], nextCursor: null }));
  expect(screen.queryByText('提供コースの訂正')).not.toBeInTheDocument();
});
