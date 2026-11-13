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
jest.mock('../api/dashboard', () => ({
  dashboardApi: {
    getTodayOrders: jest.fn(),
    getPendingOrderApplications: jest.fn(),
    getPendingShiftRequests: jest.fn(),
    getPendingReviews: jest.fn(),
    getSales: jest.fn(),
  },
}));

const getTodayOrders = jest.mocked(dashboardApi.getTodayOrders);
const getPendingOrderApplications = jest.mocked(dashboardApi.getPendingOrderApplications);
const getPendingShiftRequests = jest.mocked(dashboardApi.getPendingShiftRequests);
const getPendingReviews = jest.mocked(dashboardApi.getPendingReviews);
const PENDING_REGIONS: PermissionCode[] = [
  'DASHBOARD_PENDING_ORDER_APPLICATIONS_VIEW',
  'DASHBOARD_PENDING_SHIFT_REQUESTS_VIEW',
  'DASHBOARD_PENDING_REVIEWS_VIEW',
];
const getSales = jest.mocked(dashboardApi.getSales);
const SALES = {
  business_date: '2026-11-12',
  today: { sales: 20_000, order_count: 2 },
  month_to_date: { sales: 12_345_678, order_count: 40 },
  previous_month_to_date: { sales: 10_000_000, order_count: 35 },
  daily: [
    { business_date: '2026-11-01', sales: 5_000, order_count: 1 },
    { business_date: '2026-11-02', sales: 0, order_count: 0 },
  ],
};
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
const dailySalesRegion = () =>
  screen.queryByRole('heading', { name: '今月の日別の売上', level: 2 });
const statOf = (note: string) => screen.getByText(note).parentElement;

const pendingWorkRegion = () => screen.queryByRole('heading', { name: 'やること', level: 2 });
const row = async (label: string) => (await screen.findByText(label)).parentElement;

beforeEach(() => {
  getTodayOrders.mockReset();
  getPendingOrderApplications
    .mockReset()
    .mockResolvedValue({ business_date: '2026-11-12', count: 3 });
  getPendingShiftRequests.mockReset().mockResolvedValue({ count: 5 });
  getPendingReviews.mockReset().mockResolvedValue({ count: 2 });
  getSales.mockReset();
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

  describe('やること', () => {
    it('区画の権限で未処理の件数を出し、一覧の権限が無ければ導線を出さない', async () => {
      withMe(PENDING_REGIONS);

      render(<StoreDashboardPage />);

      expect(pendingWorkRegion()).toBeInTheDocument();
      expect(await row('未処理の予約申請')).toHaveTextContent('未処理の予約申請3件');
      expect(await row('未処理の出勤希望')).toHaveTextContent('未処理の出勤希望5件');
      expect(await row('審査待ちの口コミ')).toHaveTextContent('審査待ちの口コミ2件');
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('一覧の権限ごとに、件数からその一覧へ進める', async () => {
      withMe([...PENDING_REGIONS, 'ORDER_MANAGE', 'SHIFT_MANAGE', 'REVIEW_VIEW']);

      render(<StoreDashboardPage />);

      expect(await screen.findByRole('link', { name: /未処理の予約申請\s*3/ })).toHaveAttribute(
        'href',
        '/store/2/orders'
      );
      expect(await screen.findByRole('link', { name: /未処理の出勤希望\s*5/ })).toHaveAttribute(
        'href',
        '/store/2/shifts'
      );
      expect(await screen.findByRole('link', { name: /審査待ちの口コミ\s*2/ })).toHaveAttribute(
        'href',
        '/store/2/reviews'
      );
    });

    it('導線は行ごとの一覧の権限で決まり、他の行の一覧の権限では出ない', async () => {
      withMe([...PENDING_REGIONS, 'SHIFT_MANAGE']);

      render(<StoreDashboardPage />);

      expect(await screen.findByRole('link', { name: /未処理の出勤希望/ })).toBeInTheDocument();
      expect(await row('未処理の予約申請')).not.toHaveAttribute('href');
      expect(screen.getAllByRole('link')).toHaveLength(1);
    });

    it('区画の権限が無い行は出さず取得もせず、一つも無ければカードごと出さない', async () => {
      withMe(['DASHBOARD_PENDING_SHIFT_REQUESTS_VIEW', 'ORDER_MANAGE', 'REVIEW_VIEW']);

      const { unmount } = render(<StoreDashboardPage />);

      expect(await row('未処理の出勤希望')).toHaveTextContent('5件');
      expect(screen.queryByText('未処理の予約申請')).not.toBeInTheDocument();
      expect(screen.queryByText('審査待ちの口コミ')).not.toBeInTheDocument();
      expect(getPendingOrderApplications).not.toHaveBeenCalled();
      expect(getPendingReviews).not.toHaveBeenCalled();
      unmount();

      withMe(['ORDER_MANAGE', 'SHIFT_MANAGE', 'REVIEW_VIEW']);
      render(<StoreDashboardPage />);

      expect(pendingWorkRegion()).not.toBeInTheDocument();
      expect(getPendingShiftRequests).toHaveBeenCalledTimes(1);
    });

    it('一つの行の取得が失敗しても他の行は出て、その行にだけ再試行を出す', async () => {
      withMe(PENDING_REGIONS);
      getPendingReviews.mockReset().mockRejectedValueOnce(new Error('network')).mockResolvedValue({
        count: 2,
      });

      render(<StoreDashboardPage />);

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent('審査待ちの口コミの件数の取得に失敗しました');
      expect(await row('未処理の予約申請')).toHaveTextContent('3件');
      expect(await row('未処理の出勤希望')).toHaveTextContent('5件');

      fireEvent.click(within(alert).getByRole('button'));

      expect(await row('審査待ちの口コミ')).toHaveTextContent('審査待ちの口コミ2件');
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
  });

  it('売上の区画の権限で今日・今月の売上を出し、増減は今月だけに前月同時点と並べる', async () => {
    withMe(['DASHBOARD_SALES_VIEW']);
    getSales.mockResolvedValue(SALES);

    render(<StoreDashboardPage />);

    expect(await screen.findByText('今日の売上')).toBeInTheDocument();
    expect(statOf('今日の売上')).toHaveTextContent('¥20,000');
    expect(statOf('今日の売上')).toHaveTextContent('完了 2 件');
    expect(statOf('今日の売上')).not.toHaveTextContent('前月同時点');
    expect(statOf('今月の売上')).toHaveTextContent('¥12,345,678');
    expect(statOf('今月の売上')).toHaveTextContent('+23.5%');
    expect(statOf('今月の売上')).toHaveTextContent('前月同時点 ¥10,000,000');
    expect(dailySalesRegion()).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('前月同時点を下回れば減少を、前月同時点が 0 なら率を出さず金額だけを出す', async () => {
    withMe(['DASHBOARD_SALES_VIEW']);
    getSales
      .mockResolvedValueOnce({
        ...SALES,
        month_to_date: { sales: 7_500, order_count: 1 },
        previous_month_to_date: { sales: 10_000, order_count: 2 },
      })
      .mockResolvedValueOnce({ ...SALES, previous_month_to_date: { sales: 0, order_count: 0 } });

    const { unmount } = render(<StoreDashboardPage />);
    expect((await screen.findByText('今月の売上')).parentElement).toHaveTextContent('−25.0%');
    unmount();

    render(<StoreDashboardPage />);
    const month = (await screen.findByText('今月の売上')).parentElement;
    expect(month).toHaveTextContent('前月同時点 ¥0');
    expect(month).not.toHaveTextContent('%');
  });

  it('運営集計を開ける者には、売上の金額から運営集計へ進む導線を出す', async () => {
    withMe(['DASHBOARD_SALES_VIEW', 'OPERATIONAL_REPORT_VIEW', 'ORDER_MANAGE']);
    getSales.mockResolvedValue(SALES);

    render(<StoreDashboardPage />);

    const link = await screen.findByRole('link', { name: '¥12,345,678' });
    expect(link).toHaveAttribute('href', '/store/2/operational-reports');
    expect(screen.getByRole('link', { name: '¥20,000' })).toHaveAttribute(
      'href',
      '/store/2/operational-reports'
    );
  });

  it('売上の区画の権限が無ければ、運営集計の権限があっても売上を出さず取得もしない', async () => {
    withMe(['DASHBOARD_TODAY_ORDERS_VIEW', 'OPERATIONAL_REPORT_VIEW', 'ORDER_MANAGE']);
    getTodayOrders.mockResolvedValue(TODAY_ORDERS);

    render(<StoreDashboardPage />);

    expect(await screen.findByText('確定')).toBeInTheDocument();
    expect(todayOrdersRegion()).toBeInTheDocument();
    expect(screen.queryByText('今日の売上')).not.toBeInTheDocument();
    expect(dailySalesRegion()).not.toBeInTheDocument();
    expect(getSales).not.toHaveBeenCalled();
    expect(document.querySelector('[data-column="numbers"]')).toBeNull();
    expect(todayOrdersRegion()?.closest('[data-column]')).toHaveAttribute(
      'data-column',
      'today-full-width'
    );
  });

  it('売上と今日の受注の両方を見られる者には、数字の列と今日の列を並べる', async () => {
    withMe(['DASHBOARD_SALES_VIEW', 'DASHBOARD_TODAY_ORDERS_VIEW']);
    getTodayOrders.mockResolvedValue(TODAY_ORDERS);
    getSales.mockResolvedValue(SALES);

    render(<StoreDashboardPage />);

    expect((await screen.findByText('今日の売上')).closest('[data-column]')).toHaveAttribute(
      'data-column',
      'numbers'
    );
    expect(todayOrdersRegion()?.closest('[data-column]')).toHaveAttribute('data-column', 'today');
  });

  it('売上の取得が失敗しても今日の受注は出し、売上の区画にだけ再試行を出す', async () => {
    withMe(['DASHBOARD_SALES_VIEW', 'DASHBOARD_TODAY_ORDERS_VIEW']);
    getTodayOrders.mockResolvedValue(TODAY_ORDERS);
    getSales.mockRejectedValueOnce(new Error('network')).mockResolvedValue(SALES);

    render(<StoreDashboardPage />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('売上の取得に失敗しました');
    expect((await screen.findByText('確定')).parentElement).toHaveTextContent('確定4件');

    fireEvent.click(within(alert).getByRole('button'));

    expect(await screen.findByText('今日の売上')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
