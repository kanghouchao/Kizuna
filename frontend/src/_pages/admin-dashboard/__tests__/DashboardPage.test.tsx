import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { useMe, type PermissionCode } from '@/entities/user';
import { dashboardApi } from '../api/dashboard';
import DashboardPage from '../ui/DashboardPage';

jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('../api/dashboard', () => ({
  dashboardApi: { getStores: jest.fn(), getSalesRanking: jest.fn() },
}));

const getStores = jest.mocked(dashboardApi.getStores);
const getSalesRanking = jest.mocked(dashboardApi.getSalesRanking);
const STORES = { total: 12, preparing: 3, active: 9 };

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

const storesRegion = () => screen.queryByRole('region', { name: '担当店舗' });

beforeEach(() => {
  getStores.mockReset();
  getSalesRanking.mockReset();
});

describe('グループのダッシュボード', () => {
  it('区画の権限があれば、担当店舗の数と稼働状態の内訳を出すこと', async () => {
    withMe(['DASHBOARD_STORES_VIEW']);
    getStores.mockResolvedValue(STORES);

    render(<DashboardPage />);

    const region = await screen.findByRole('region', { name: '担当店舗' });
    expect(await within(region).findByText('12 店舗')).toBeInTheDocument();
    expect(within(region).getByText('稼働中 9')).toBeInTheDocument();
    expect(within(region).getByText('準備中 3')).toBeInTheDocument();
  });

  it('店舗の管理の権限を持つ者にだけ、店舗数から店舗一覧への導線を出すこと', async () => {
    withMe(['DASHBOARD_STORES_VIEW', 'STORE_MANAGE']);
    getStores.mockResolvedValue(STORES);

    render(<DashboardPage />);

    expect(await screen.findByRole('link', { name: '12 店舗' })).toHaveAttribute(
      'href',
      '/admin/stores'
    );
  });

  it('店舗の管理の権限が無ければ、数字だけで導線を出さないこと', async () => {
    withMe(['DASHBOARD_STORES_VIEW']);
    getStores.mockResolvedValue(STORES);

    render(<DashboardPage />);

    expect(await screen.findByText('12 店舗')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('区画の権限が無ければ、店舗の管理の権限があっても区画を出さず取得もしないこと', () => {
    withMe(['STORE_MANAGE', 'STAFF_MANAGE']);

    render(<DashboardPage />);

    expect(storesRegion()).not.toBeInTheDocument();
    expect(getStores).not.toHaveBeenCalled();
    expect(
      screen.getByText('このダッシュボードで表示できる項目がありません。')
    ).toBeInTheDocument();
  });

  it('利用者の情報を待つ間は区画を出さないこと', () => {
    withMe(null);

    render(<DashboardPage />);

    expect(storesRegion()).not.toBeInTheDocument();
    expect(getStores).not.toHaveBeenCalled();
  });
});

describe('担当店舗の取得失敗', () => {
  beforeEach(() => {
    withMe(['DASHBOARD_STORES_VIEW']);
  });

  it('取得に失敗したら 0 店舗と言わず、区画が失敗を名乗って再試行できること', async () => {
    getStores.mockRejectedValueOnce(new Error('boom'));

    render(<DashboardPage />);

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('担当店舗の取得に失敗しました')).toBeInTheDocument();
    // 読めなかっただけの状態が「0 店舗」という事実に化ける
    expect(screen.queryByText('0 店舗')).not.toBeInTheDocument();

    getStores.mockResolvedValue(STORES);
    fireEvent.click(within(alert).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('12 店舗')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('二度目の失敗でも読み込み中を経由し、区画が mount し直されること', async () => {
    getStores.mockRejectedValueOnce(new Error('boom'));

    render(<DashboardPage />);
    const alert = await screen.findByRole('alert');

    // 2 回目の解決を保留し、押した直後の姿を観測する
    let failSecond = (): void => {};
    getStores.mockReturnValueOnce(
      new Promise((_, reject) => {
        failSecond = () => reject(new Error('boom again'));
      })
    );
    fireEvent.click(within(alert).getByRole('button', { name: '再試行' }));

    // 読み込み中を経由するので、この時点で区画は一度消えている。消えないまま二度目の失敗を
    // 迎えると role="alert" が再発火せず、読み上げ利用者には何も届かない
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    await act(async () => {
      failSecond();
    });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});

const rankedStore = (id: number, sales: number, status: 'ACTIVE' | 'PREPARING' = 'ACTIVE') => ({
  store_id: id,
  store_name: `店舗${id}`,
  store_status: status,
  sales,
  order_count: sales / 1000,
});
const rankingRegion = () => screen.queryByRole('region', { name: '店舗別の今月の売上' });

describe('店舗別の売上順位', () => {
  it('全店を応答の順に並べ切り、各行からその店舗のダッシュボードへ進めること', async () => {
    withMe(['DASHBOARD_SALES_RANKING_VIEW']);
    const stores = Array.from({ length: 40 }, (_, i) => rankedStore(i + 1, (40 - i) * 1000));
    getSalesRanking.mockResolvedValue({ business_date: '2026-11-12', stores });

    render(<DashboardPage />);

    const region = await screen.findByRole('region', { name: '店舗別の今月の売上' });
    const rows = await within(region).findAllByRole('link');
    expect(rows).toHaveLength(40);
    expect(rows[0]).toHaveAttribute('href', '/store/1/dashboard');
    expect(rows[0]).toHaveTextContent('店舗1');
    expect(rows[39]).toHaveAttribute('href', '/store/40/dashboard');
    expect(rows[39]).toHaveTextContent('店舗40');
  });

  it('準備中の店舗の行にだけ状態ラベルを付けること', async () => {
    withMe(['DASHBOARD_SALES_RANKING_VIEW']);
    getSalesRanking.mockResolvedValue({
      business_date: '2026-11-12',
      stores: [rankedStore(1, 5000), rankedStore(2, 0, 'PREPARING')],
    });

    render(<DashboardPage />);

    const rows = await within(
      await screen.findByRole('region', { name: '店舗別の今月の売上' })
    ).findAllByRole('link');
    expect(within(rows[0]).queryByText('準備中')).not.toBeInTheDocument();
    expect(within(rows[1]).getByText('準備中')).toBeInTheDocument();
  });

  it('担当店舗が無ければ、空の順位表でなくそう告げること', async () => {
    withMe(['DASHBOARD_SALES_RANKING_VIEW']);
    getSalesRanking.mockResolvedValue({ business_date: '2026-11-12', stores: [] });

    render(<DashboardPage />);

    expect(await screen.findByText('担当店舗がありません。')).toBeInTheDocument();
    expect(
      screen.queryByText('このダッシュボードで表示できる項目がありません。')
    ).not.toBeInTheDocument();
  });

  it('区画の権限が無ければ区画を出さず取得もしないこと', () => {
    withMe(['DASHBOARD_STORES_VIEW', 'OPERATIONAL_REPORT_SET_VIEW']);
    getStores.mockResolvedValue(STORES);

    render(<DashboardPage />);

    expect(rankingRegion()).not.toBeInTheDocument();
    expect(getSalesRanking).not.toHaveBeenCalled();
  });

  it('順位の取得に失敗しても、その区画だけが失敗を名乗り担当店舗の区画は描かれること', async () => {
    withMe(['DASHBOARD_STORES_VIEW', 'DASHBOARD_SALES_RANKING_VIEW']);
    getStores.mockResolvedValue(STORES);
    getSalesRanking.mockRejectedValueOnce(new Error('boom'));

    render(<DashboardPage />);

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('店舗別の売上の取得に失敗しました')).toBeInTheDocument();
    expect(await screen.findByText('12 店舗')).toBeInTheDocument();

    getSalesRanking.mockResolvedValue({ business_date: '2026-11-12', stores: [rankedStore(1, 0)] });
    fireEvent.click(within(alert).getByRole('button', { name: '再試行' }));

    expect(await screen.findByRole('link', { name: /店舗1/ })).toBeInTheDocument();
  });
});
