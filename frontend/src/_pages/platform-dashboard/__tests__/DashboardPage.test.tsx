import { render, screen } from '@testing-library/react';
import { platformStoreApi } from '@/entities/store';
import DashboardPage from '../ui/DashboardPage';

jest.mock('@/entities/store', () => ({
  platformStoreApi: {
    getStats: jest.fn(),
  },
}));

jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: jest.fn() }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = platformStoreApi as jest.Mocked<typeof platformStoreApi>;

describe('プラットフォームダッシュボード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.getStats.mockResolvedValue({ total: 12 });
  });

  it('総店舗数を表示すること', async () => {
    render(<DashboardPage />);

    expect(await screen.findByText('総店舗数')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
  });

  it('未モデル化の店舗状態は統計として表示しないこと', async () => {
    render(<DashboardPage />);
    await screen.findByText('総店舗数');

    expect(screen.queryByText('審査待ち')).not.toBeInTheDocument();
    expect(screen.queryByText('有効店舗')).not.toBeInTheDocument();
    expect(screen.queryByText('無効店舗')).not.toBeInTheDocument();
  });

  it('店舗一覧と店舗作成の導線は持たないこと', async () => {
    render(<DashboardPage />);
    await screen.findByText('総店舗数');

    expect(screen.queryByText('最近追加された店舗')).not.toBeInTheDocument();
    expect(screen.queryByText('店舗作成')).not.toBeInTheDocument();
    expect(screen.queryByText('店舗管理')).not.toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
