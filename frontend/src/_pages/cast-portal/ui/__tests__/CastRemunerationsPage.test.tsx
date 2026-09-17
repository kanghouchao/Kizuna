import { fireEvent, render, screen } from '@testing-library/react';
import { CastRemunerationsPage } from '../CastRemunerationsPage';
import { selfRemunerationApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  selfRemunerationApi: {
    enrollments: jest.fn(),
    list: jest.fn(),
    detail: jest.fn(),
    changes: jest.fn(),
  },
}));

const enrollment = {
  enrollment_id: 'old',
  store_id: 1,
  store_name: '退店店舗',
  status: 'WITHDRAWN' as const,
  ended_at: '2026-09-16T10:00:00Z',
};
const summary = {
  order_id: 'order',
  enrollment_id: 'old',
  store_id: 1,
  store_name: '退店店舗',
  business_date: '2026-09-14',
  completed_at: '2026-09-15T01:00:00+09:00',
  status: 'COMPLETED' as const,
  completion_invalidated: false,
  agreed_remuneration: 7000,
  planned_remuneration: 0,
  accrued_remuneration: 7000,
};
const item = {
  kind: 'COURSE' as const,
  name: '基本コース',
  price: 12000,
  remuneration: 7000,
  duration_minutes: 60,
};
const page = <T,>(rows: T[]) => ({ rows, page: 0, pageCount: 1, total: rows.length });
beforeEach(() => {
  jest.resetAllMocks();
  jest.mocked(selfRemunerationApi.enrollments).mockResolvedValue(page([enrollment]));
  jest.mocked(selfRemunerationApi.list).mockResolvedValue(page([summary]));
  jest
    .mocked(selfRemunerationApi.detail)
    .mockResolvedValue({ ...summary, version: 1, items: [item] });
  jest.mocked(selfRemunerationApi.changes).mockResolvedValue({ rows: [], nextCursor: null });
});
it('opens a withdrawn enrollment order and its history from the portal', async () => {
  render(<CastRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '詳細を確認' }));
  expect(await screen.findByText('基本コース')).toBeInTheDocument();
  expect(screen.getByText('支払済み額ではありません。')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '変更履歴を確認' }));
  expect(await screen.findByText('変更履歴はありません')).toBeInTheDocument();
  expect(selfRemunerationApi.detail).toHaveBeenCalledWith('order');
});
it('distinguishes failure from empty data and retries the list', async () => {
  jest.mocked(selfRemunerationApi.list).mockRejectedValueOnce(new Error('network'));
  render(<CastRemunerationsPage />);
  await screen.findByText('報酬明細の取得に失敗しました');
  expect(screen.queryByText('報酬明細はありません')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
  expect(await screen.findByRole('button', { name: '詳細を確認' })).toBeInTheDocument();
});
it('refuses to display stale private data when details are forbidden', async () => {
  jest.mocked(selfRemunerationApi.detail).mockRejectedValue({ response: { status: 403 } });
  render(<CastRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '詳細を確認' }));
  await screen.findByText('報酬明細を確認する権限がありません。');
  expect(screen.queryByText('基本コース')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '変更履歴を確認' })).not.toBeInTheDocument();
});
it('filters by the withdrawn enrollment and keeps its history accessible', async () => {
  render(<CastRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '退店店舗・退店（2026-09-16）' }));
  await screen.findByRole('button', { name: '詳細を確認' });
  expect(selfRemunerationApi.list).toHaveBeenLastCalledWith(0, 'old');
});
it('distinguishes missing details from a failed request', async () => {
  jest.mocked(selfRemunerationApi.detail).mockRejectedValue({ response: { status: 404 } });
  render(<CastRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '詳細を確認' }));
  await screen.findByText('対象の在籍または報酬明細が見つかりません。');
  expect(screen.queryByText('報酬明細はありません')).not.toBeInTheDocument();
});
it('shows invalidation before and after without describing original terms as paid', async () => {
  jest.mocked(selfRemunerationApi.detail).mockResolvedValue({
    ...summary,
    version: 2,
    items: [item],
    completion_invalidated: true,
    accrued_remuneration: 0,
  });
  jest.mocked(selfRemunerationApi.changes).mockResolvedValue({
    rows: [
      {
        change_id: 'change',
        change_type: 'COMPLETION_INVALIDATION',
        order_id: 'order',
        business_date: summary.business_date,
        completed_at: summary.completed_at,
        changed_at: '2026-09-16T10:00:00Z',
        reason: '未提供のため無効化',
        before_version: 1,
        after_version: 2,
        before: {
          items: [item],
          agreed_remuneration: 7000,
          accrued_remuneration: 7000,
          completion_invalidated: false,
        },
        after: {
          items: [item],
          agreed_remuneration: 7000,
          accrued_remuneration: 0,
          completion_invalidated: true,
        },
      },
    ],
    nextCursor: null,
  });
  render(<CastRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '詳細を確認' }));
  await screen.findByText('無効化（有効報酬 0 円）');
  fireEvent.click(screen.getByRole('button', { name: '変更履歴を確認' }));
  await screen.findByText('未提供のため無効化');
  expect(screen.getByText('約定報酬: 7,000 円 / 発生済み報酬: 7,000 円')).toBeInTheDocument();
  expect(screen.getByText('約定報酬: 7,000 円 / 発生済み報酬: 0 円')).toBeInTheDocument();
  expect(screen.getByText('変更 ID: change')).toBeInTheDocument();
});
it('retries history failures and respects cursor continuation', async () => {
  jest
    .mocked(selfRemunerationApi.changes)
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ rows: [], nextCursor: 'next' })
    .mockResolvedValueOnce({ rows: [], nextCursor: null });
  render(<CastRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: '詳細を確認' }));
  fireEvent.click(await screen.findByRole('button', { name: '変更履歴を確認' }));
  await screen.findByText('報酬明細の取得に失敗しました');
  expect(screen.queryByText('変更履歴はありません')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
  fireEvent.click(await screen.findByRole('button', { name: 'さらに読み込む' }));
  await screen.findByText('変更履歴はありません');
  expect(selfRemunerationApi.changes).toHaveBeenLastCalledWith('order', 'next');
});
it('shows empty results without treating them as errors', async () => {
  jest.mocked(selfRemunerationApi.list).mockResolvedValue(page([]));
  render(<CastRemunerationsPage />);
  await screen.findByText('報酬明細はありません');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
