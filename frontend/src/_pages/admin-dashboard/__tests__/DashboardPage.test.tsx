import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { adminStoreApi } from '@/entities/store';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import DashboardPage from '../ui/DashboardPage';

jest.mock('@/entities/store', () => ({
  adminStoreApi: {
    getStats: jest.fn(),
  },
}));

jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));

const mockedApi = adminStoreApi as jest.Mocked<typeof adminStoreApi>;

const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const meWith = (permissions: PermissionCode[]) =>
  mockedUseMe.mockReturnValue({
    me: {
      email: 'staff@example.com',
      user_type: 'STAFF',
      permissions,
      line_linked: false,
      email_verified: false,
    },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });

beforeEach(() => {
  meWith(['STORE_MANAGE']);
});

describe('ダッシュボード', () => {
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
    // 取得できた姿にボタンは 1 つも無い。失敗時の再試行はこの describe の射程外
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('店舗管理の権限が無い者のダッシュボード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    meWith(['ORDER_MANAGE']);
  });

  it('集計を取りに行かず、店舗の情報も出さないこと（店舗の文脈は店舗のダッシュボードにある）', () => {
    render(<DashboardPage />);

    expect(screen.queryByText('総店舗数')).not.toBeInTheDocument();
    expect(mockedApi.getStats).not.toHaveBeenCalled();
  });
});

describe('ダッシュボードの取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('取得に失敗したら 0 店舗と言わず、区画が失敗を名乗って再試行できること', async () => {
    mockedApi.getStats.mockRejectedValueOnce(new Error('boom'));

    render(<DashboardPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('店舗数の取得に失敗しました')).toBeInTheDocument();
    // 読めなかっただけの状態が「0 店舗」という事実に化ける
    expect(screen.queryByText('総店舗数')).not.toBeInTheDocument();
    expect(screen.queryByText('0')).not.toBeInTheDocument();

    mockedApi.getStats.mockResolvedValue({ total: 12 });
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('二度目の失敗でも読み込み中を経由し、区画が mount し直されること', async () => {
    mockedApi.getStats.mockRejectedValueOnce(new Error('boom'));

    render(<DashboardPage />);
    const region = await screen.findByRole('alert');

    // 2 回目の解決を保留し、押した直後の姿を観測する
    let failSecond = (): void => {};
    mockedApi.getStats.mockReturnValueOnce(
      new Promise((_, reject) => {
        failSecond = () => reject(new Error('boom again'));
      })
    );
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    // 読み込み中を経由するので、この時点で区画は一度消えている。消えないまま二度目の失敗を
    // 迎えると role="alert" が再発火せず、読み上げ利用者には何も届かない
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    await act(async () => {
      failSecond();
    });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
