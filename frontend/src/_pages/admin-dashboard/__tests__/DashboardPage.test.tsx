import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { useMe, type PermissionCode } from '@/entities/user';
import { dashboardApi } from '../api/dashboard';
import DashboardPage from '../ui/DashboardPage';

jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('../api/dashboard', () => ({ dashboardApi: { getStores: jest.fn() } }));

const getStores = jest.mocked(dashboardApi.getStores);
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
