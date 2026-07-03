import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import OrderListPage from '../ui/OrdersPage';
import CreateOrderPage from '../ui/OrderCreatePage';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    list: jest.fn(),
    create: jest.fn(),
  },
}));

jest.mock('@/entities/cast', () => ({
  castApi: {
    get: jest.fn(),
    list: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
}));

const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;

describe('店側オーダー画面と API JSON（snake_case）の整合（#214）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('一覧はバックエンドが実際に返す snake_case のフィールドを表示すること', async () => {
    // バックエンドは Jackson グローバル SNAKE_CASE（既知の実レスポンス形）
    mockedOrderApi.list.mockResolvedValue({
      content: [
        {
          id: '1',
          store_name: '沼津H',
          business_date: '2026-07-03',
          customer_name: '山田太郎',
          cast_name: '花子',
          course_minutes: 60,
          extension_minutes: 0,
          option_codes: [],
          manual_discount: 0,
          used_points: 0,
          manual_grant_points: 0,
          status: 'CREATED',
        },
      ],
      total_pages: 1,
      total_elements: 1,
      size: 100,
      number: 0,
    } as never);

    render(<OrderListPage />);

    expect(await screen.findByText('沼津H')).toBeInTheDocument();
    expect(screen.getByText('2026-07-03')).toBeInTheDocument();
    expect(screen.getByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('花子')).toBeInTheDocument();
    expect(screen.getByText('60 分')).toBeInTheDocument();
  });

  it('新規登録はバックエンドの DTO に合わせ snake_case キーで POST すること', async () => {
    mockedOrderApi.create.mockResolvedValue({} as never);

    render(<CreateOrderPage />);
    fireEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() => expect(mockedOrderApi.create).toHaveBeenCalledTimes(1));
    const body = mockedOrderApi.create.mock.calls[0][0] as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('store_name', '沼津H');
    expect(body).toHaveProperty('business_date');
    expect(body).toHaveProperty('course_minutes', 60);
    expect(body).not.toHaveProperty('storeName');
    expect(body).not.toHaveProperty('businessDate');
  });
});
