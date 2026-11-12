import { fireEvent, render, screen, within } from '@testing-library/react';
import { useMe, useStoreContext, type PermissionCode } from '@/entities/user';
import { dashboardApi } from '../api/dashboard';
import StoreDashboardPage from '../ui/StoreDashboardPage';

jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/entities/user/model/StoreContext', () => ({
  ...jest.requireActual('@/entities/user/model/StoreContext'),
  useStoreContext: jest.fn(),
}));
jest.mock('../api/dashboard', () => ({ dashboardApi: { getTodayOrders: jest.fn() } }));

const getTodayOrders = jest.mocked(dashboardApi.getTodayOrders);
const TODAY_ORDERS = {
  business_date: '2026-11-12',
  confirmed: 4,
  in_service: 2,
  completed: 7,
  cancelled: 1,
};

const withStore = (status: 'PREPARING' | 'ACTIVE', name = '新宿店') =>
  jest.mocked(useStoreContext).mockReturnValue({
    stores: [{ id: 2, name, status }],
    currentStoreId: '2',
    currentStore: { id: 2, name, status },
    loadFailed: false,
    reload: jest.fn(),
  });

const withMe = (permissions: PermissionCode[] | null) =>
  jest.mocked(useMe).mockReturnValue({
    me:
      permissions === null
        ? null
        : {
            email: 'staff@example.com',
            user_type: 'STAFF',
            permissions,
            line_linked: false,
            email_verified: false,
          },
    isLoading: permissions === null,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });

const todayOrdersRegion = () => screen.queryByRole('heading', { name: '今日の受注', level: 2 });

beforeEach(() => {
  getTodayOrders.mockReset();
  withStore('ACTIVE');
});

describe('店舗のダッシュボード', () => {
  it.each([
    ['PREPARING', '準備中'],
    ['ACTIVE', '稼働中'],
  ] as const)('見出しに店名と稼働状態（%s）を出し、店舗名と状態のカードは無い', (status, label) => {
    withStore(status);
    withMe([]);

    render(<StoreDashboardPage />);

    expect(screen.getByRole('heading', { level: 1, name: '新宿店' })).toBeInTheDocument();
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.queryByText('店舗')).not.toBeInTheDocument();
  });

  it('区画の権限で今日の受注の件数を出し、受注の一覧の権限が無ければ導線を出さない', async () => {
    withMe(['DASHBOARD_TODAY_ORDERS_VIEW']);
    getTodayOrders.mockResolvedValue(TODAY_ORDERS);

    render(<StoreDashboardPage />);

    const list = await screen.findByRole('list');
    expect(within(list).getByText('確定').parentElement).toHaveTextContent('確定4件');
    expect(within(list).getByText('サービス中').parentElement).toHaveTextContent('サービス中2件');
    expect(within(list).getByText('完了').parentElement).toHaveTextContent('完了7件');
    expect(within(list).getByText('キャンセル').parentElement).toHaveTextContent('キャンセル1件');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('受注の一覧の権限があれば、件数から今日の営業日の受注の一覧へ進める', async () => {
    withMe(['DASHBOARD_TODAY_ORDERS_VIEW', 'ORDER_MANAGE']);
    getTodayOrders.mockResolvedValue(TODAY_ORDERS);

    render(<StoreDashboardPage />);

    const link = await screen.findByRole('link', { name: /確定\s*4/ });
    expect(link).toHaveAttribute('href', '/store/2/orders?business_date=2026-11-12');
  });

  it('区画の権限が無ければ、受注の一覧の権限があっても区画を出さず取得もしない', () => {
    withMe(['ORDER_MANAGE']);

    render(<StoreDashboardPage />);

    expect(todayOrdersRegion()).not.toBeInTheDocument();
    expect(getTodayOrders).not.toHaveBeenCalled();
    expect(
      screen.getByText('このダッシュボードで表示できる項目がありません。')
    ).toBeInTheDocument();
  });

  it('権限の取得中は空の画面を出さない', () => {
    withMe(null);

    render(<StoreDashboardPage />);

    expect(
      screen.queryByText('このダッシュボードで表示できる項目がありません。')
    ).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: '新宿店' })).toBeInTheDocument();
  });

  it('区画の取得が失敗しても見出しは残り、その区画にだけ再試行を出す', async () => {
    withMe(['DASHBOARD_TODAY_ORDERS_VIEW']);
    getTodayOrders.mockRejectedValueOnce(new Error('network')).mockResolvedValue(TODAY_ORDERS);

    render(<StoreDashboardPage />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('今日の受注件数の取得に失敗しました');
    expect(screen.getByRole('heading', { level: 1, name: '新宿店' })).toBeInTheDocument();
    expect(todayOrdersRegion()).toBeInTheDocument();

    fireEvent.click(within(alert).getByRole('button'));

    expect((await screen.findByText('確定')).parentElement).toHaveTextContent('確定4件');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
