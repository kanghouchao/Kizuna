import { fireEvent, render, screen } from '@testing-library/react';
import PlatformOrdersPage from '../ui/PlatformOrdersPage';
import { orderApi } from '@/entities/order';
import { hasPermission } from '@/shared/lib';

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  hasPermission: jest.fn(() => true),
}));
jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: { platformList: jest.fn(), correctionHistory: jest.fn() },
}));
beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(hasPermission).mockReturnValue(true);
});
it('参照権限だけで一覧と履歴を開き、訂正操作は表示しない', async () => {
  jest.mocked(orderApi.platformList).mockResolvedValue({
    rows: [
      {
        id: 'o1',
        store_id: 2,
        status: 'COMPLETED',
        business_date: '2026-09-14',
        completed_at: '2026-09-15T01:00:00Z',
        accrued_remuneration: 7000,
        total_remuneration: 7000,
        course: {
          service_id: 's1',
          revision_id: 'r1',
          revision_number: 1,
          name: '標準',
          duration_minutes: 60,
          price: 12000,
          remuneration: 7000,
          adoption_basis: 'CURRENT_SETTING',
          adopted_at: '2026-09-14T00:00:00Z',
        },
      },
    ],
    total: 1,
    page: 0,
    pageCount: 1,
  });
  jest.mocked(orderApi.correctionHistory).mockResolvedValue({ rows: [], nextCursor: null });
  render(<PlatformOrdersPage />);
  expect(await screen.findByText('発生済み報酬 ¥7,000')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '訂正履歴' }));
  expect(await screen.findByText('訂正履歴はありません')).toBeInTheDocument();
  expect(orderApi.correctionHistory).toHaveBeenCalledWith('platform', 'o1', undefined);
  expect(screen.queryByRole('button', { name: '訂正する' })).not.toBeInTheDocument();
});
it('権限のない利用者には一覧を取得せず拒否を示す', async () => {
  jest.mocked(hasPermission).mockReturnValue(false);
  render(<PlatformOrdersPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('受注を閲覧する権限がありません');
  expect(orderApi.platformList).not.toHaveBeenCalled();
});
