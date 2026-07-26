import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PaginatedResponse } from '@/shared/api';
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
  is_active: true,
  created_at: '2026-01-01T00:00:00Z',
  ...override,
});

const paginated = (data: Store[]): PaginatedResponse<Store> => ({
  data,
  current_page: 1,
  from: 1,
  last_page: 1,
  per_page: 5,
  to: data.length,
  total: data.length,
  first_page_url: '',
  last_page_url: '',
  next_page_url: null,
  prev_page_url: null,
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
    expect(mockedApi.getList).toHaveBeenCalledWith({ per_page: 5, page: 1 });
  });

  it('店舗追加ボタンで作成画面へ遷移すること', async () => {
    render(<DashboardPage />);

    fireEvent.click(await screen.findByRole('button', { name: '店舗追加' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/platform/stores/create'));
  });
});
