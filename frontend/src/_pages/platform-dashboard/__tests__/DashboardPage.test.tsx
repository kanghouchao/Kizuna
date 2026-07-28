import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PageResult } from '@/shared/api';
import { Store, platformStoreApi } from '@/entities/store';
import DashboardPage from '../ui/DashboardPage';

const mockPush = jest.fn();

jest.mock('@/entities/store', () => ({
  platformStoreApi: {
    getStats: jest.fn(),
    getList: jest.fn(),
  },
}));

jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: jest.fn() }),
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: Object.assign(jest.fn(), { success: jest.fn(), error: jest.fn() }),
}));

const mockedApi = platformStoreApi as jest.Mocked<typeof platformStoreApi>;

const store = (override: Partial<Store>): Store => ({
  id: '1',
  name: 'アルファ店',
  email: 'alpha@example.com',
  domain: 'alpha.example.com',
  domains: ['alpha.example.com'],
  created_at: '2026-01-01T00:00:00Z',
  ...override,
});

const paginated = (rows: Store[]): PageResult<Store> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
});

describe('プラットフォームダッシュボード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.getStats.mockResolvedValue({ total: 12, active: 7, inactive: 3, pending: 2 });
    mockedApi.getList.mockResolvedValue(paginated([store({ name: 'アルファ店' })]));
  });

  it('統計値と直近追加店舗を表示すること', async () => {
    render(<DashboardPage />);

    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('アルファ店')).toBeInTheDocument();
    expect(mockedApi.getList).toHaveBeenCalledWith({ page: 0, size: 5 });
  });

  it('店舗追加ボタンで作成画面へ遷移すること', async () => {
    render(<DashboardPage />);

    fireEvent.click(await screen.findByRole('button', { name: '店舗追加' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/platform/stores/create'));
  });
});
